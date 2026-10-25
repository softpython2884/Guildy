package dev.guildy.builds;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.villager.WorkZone;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The "Plan de chantier" item and the placement of a new site: the plan is bound to a schematic of the list; a right
 * click on a block anchors the preview on the top face of that block (the schematic in front of the player, facing its
 * look), the adjust menu then rotates, mirrors and moves it, and confirming creates the site after the checks:
 * the claim dimension, inside the guild's territory (builds.json5) and never in another guild's land, within the
 * world's height, no overlap with another site, never over a heart, a fanion or a guild construction, the guild's
 * limit of active sites. The schematic is copied into the world folder at once.
 */
public final class BuildPlacement {
	public static final String KIND_BUILD_PLAN = "build_plan";

	private BuildPlacement() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				Safe.get("build.plan.use_block", () -> onUseBlock(player, level, hand, hit), InteractionResult.PASS));
		UseItemCallback.EVENT.register((player, level, hand) ->
				Safe.get("build.plan.use_item", () -> onUseItem(player, level, hand), InteractionResult.PASS));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				Safe.run("build.preview.disconnect", () -> BuildPreview.forget(handler.getPlayer().getUUID())));
	}

	// ================================================================ item

	/** A plan bound to a schematic of the list (one per player at a time: older plans are replaced). */
	static ItemStack create(Guild guild, SchematicLibrary.Entry entry, BuildPlan plan) {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag extra = new CompoundTag();
		extra.putString("schematic", entry.key());
		extra.putString("guild", guild.id.toString());
		GuildyItems.tag(stack, KIND_BUILD_PLAN, extra);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		SchematicData data = plan.data;
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("build.plan.lore.file", entry.display()));
		lore.add(Lang.get("build.plan.lore.size", data.sizeX, data.sizeY, data.sizeZ, data.nonAir));
		lore.addAll(dev.guildy.gui.GuiUtil.lines("build.plan.lore.use"));
		return ItemUtil.named(stack, Lang.get("build.plan.name", displayName(entry)), lore);
	}

	static boolean isPlan(ItemStack stack) {
		return GuildyItems.is(stack, KIND_BUILD_PLAN);
	}

	/** The player has a placement preview in progress. */
	public static boolean previewing(ServerPlayer player) {
		return BuildPreview.session(player) != null;
	}

	/** Gives a plan (removing the player's other build plans). */
	static void give(ServerPlayer player, Guild guild, SchematicLibrary.Entry entry, BuildPlan plan) {
		removePlans(player);
		ItemUtil.giveOrDrop(player, create(guild, entry, plan));
		Msg.send(player, "build.plan.given", displayName(entry));
		Msg.sound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
	}

	static void removePlans(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isPlan(inventory.getItem(i))) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** "Maison de la forge" from "maisons/maison_de_la_forge.schem", "Moulin" from "minecraft:moulin". */
	static String displayName(SchematicLibrary.Entry entry) {
		String name = entry.display();
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
		name = name.substring(slash + 1);
		int dot = name.lastIndexOf('.');
		if (dot > 0 && !entry.world()) {
			name = name.substring(0, dot);
		}
		name = name.replace('_', ' ').replace('-', ' ').trim();
		if (name.isEmpty()) {
			return "Chantier";
		}
		return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}

	// ================================================================ use

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = serverPlayer.getItemInHand(hand);
		if (!isPlan(stack)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		Guild guild = check(serverPlayer, stack);
		if (guild == null) {
			return InteractionResult.FAIL;
		}
		BuildPreview.Session session = BuildPreview.session(serverPlayer);
		if (serverPlayer.isShiftKeyDown() && session != null) {
			BuildMenus.openAdjust(serverPlayer, guild);
			return InteractionResult.SUCCESS;
		}
		if (!GuildyServer.isClaimLevel(serverLevel)) {
			Msg.send(serverPlayer, "build.error.dimension");
			return InteractionResult.FAIL;
		}
		String key = GuildyItems.data(stack).getStringOr("schematic", "");
		BlockPos clicked = hit.getBlockPos();
		BlockPos anchor = hit.getDirection() == Direction.UP || !serverLevel.getBlockState(clicked).canBeReplaced() ? clicked.above() : clicked;
		if (session != null && session.entryKey.equals(key)) {
			// Same schematic: only the anchor moves (rotation and mirror kept).
			PlacementTransform t = session.transform;
			session.transform = anchorWith(anchor, serverPlayer.getYRot(), session.plan, t.turn(), t.flip());
			BuildPreview.changed(serverPlayer, session);
			Msg.actionBar(serverPlayer, "build.preview.moved");
			BuildMenus.openAdjust(serverPlayer, guild);
			return InteractionResult.SUCCESS;
		}
		SchematicLibrary.Entry entry = SchematicLibrary.find(key);
		if (entry == null) {
			Msg.send(serverPlayer, "build.error.schematic_gone");
			GuiErrors.sound(serverPlayer);
			return InteractionResult.FAIL;
		}
		UUID playerId = serverPlayer.getUUID();
		SchematicLibrary.Result result = SchematicLibrary.get(entry, loaded -> {
			ServerPlayer online = GuildyServer.player(playerId);
			if (online != null && loaded.ok() && isPlan(online.getMainHandItem())) {
				startPreview(online, guild, entry, loaded.plan(), anchor);
			} else if (online != null && !loaded.ok()) {
				Msg.send(online, "build.error.unusable", Lang.raw(loaded.error(), loaded.args()));
			}
		});
		if (result == null) {
			Msg.actionBar(serverPlayer, "build.preview.loading");
			return InteractionResult.SUCCESS;
		}
		if (!result.ok()) {
			Msg.send(serverPlayer, "build.error.unusable", Lang.raw(result.error(), result.args()));
			GuiErrors.sound(serverPlayer);
			return InteractionResult.FAIL;
		}
		startPreview(serverPlayer, guild, entry, result.plan(), anchor);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = serverPlayer.getItemInHand(hand);
		if (!isPlan(stack)) {
			return InteractionResult.PASS;
		}
		Guild guild = check(serverPlayer, stack);
		if (guild == null) {
			return InteractionResult.FAIL;
		}
		if (BuildPreview.session(serverPlayer) != null) {
			BuildMenus.openAdjust(serverPlayer, guild);
		} else {
			Msg.actionBar(serverPlayer, "build.plan.hint");
		}
		return InteractionResult.SUCCESS;
	}

	/** The player's guild when it may use this plan (VILLAGERS permission, its own guild's plan). */
	private static @Nullable Guild check(ServerPlayer player, ItemStack stack) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return null;
		}
		UUID owner = GuildyItems.guildOf(stack);
		if (owner != null && !owner.equals(guild.id)) {
			Msg.send(player, "build.plan.not_yours");
			return null;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return null;
		}
		return guild;
	}

	private static PlacementTransform anchorWith(BlockPos anchor, float yaw, BuildPlan plan, PlacementTransform.Turn turn, PlacementTransform.Flip flip) {
		PlacementTransform facing = PlacementTransform.inFront(anchor.getX(), anchor.getY(), anchor.getZ(), yaw, plan.data.sizeX);
		if (facing.turn() == turn && facing.flip() == flip) {
			return facing;
		}
		// Keep the chosen orientation; centre its front edge on the anchor like the default placement.
		PlacementTransform zero = new PlacementTransform(0, anchor.getY(), 0, turn, flip);
		int mid = plan.data.sizeX / 2;
		return new PlacementTransform(anchor.getX() - zero.worldX(mid, 0), anchor.getY(), anchor.getZ() - zero.worldZ(mid, 0), turn, flip);
	}

	private static void startPreview(ServerPlayer player, Guild guild, SchematicLibrary.Entry entry, BuildPlan plan, BlockPos anchor) {
		PlacementTransform transform = PlacementTransform.inFront(anchor.getX(), anchor.getY(), anchor.getZ(), player.getYRot(), plan.data.sizeX);
		BuildPreview.Session session = BuildPreview.start(player, guild.id, entry.key(), displayName(entry), plan, transform);
		BuildPreview.changed(player, session);
		Msg.send(player, "build.preview.started", displayName(entry));
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.0F);
		BuildMenus.openAdjust(player, guild);
	}

	// ================================================================ validation

	/** Checks the preview (sets {@link BuildPreview.Session#error}). */
	static void validate(ServerPlayer player, BuildPreview.Session session) {
		Guild guild = GuildManager.get(session.guildId);
		String[] error = {null};
		Object[][] args = {new Object[0]};
		if (guild == null) {
			error[0] = "error.no_guild";
		} else {
			check(player.level(), guild, session.plan, session.transform, -1, error, args);
		}
		session.error = error[0];
		session.errorArgs = args[0];
	}

	/**
	 * Why a placement is refused (lang key in {@code error[0]}, arguments in {@code args[0]}), nothing when it is fine.
	 *
	 * @param ignore id of a site not counted (the site itself), -1 for none
	 */
	static void check(Level level, Guild guild, BuildPlan plan, PlacementTransform transform, int ignore, String[] error, Object[][] args) {
		if (!GuildyServer.isClaimLevel(level)) {
			error[0] = "build.error.dimension";
			return;
		}
		SchematicData data = plan.data;
		int[] box = transform.box(data.sizeX, data.sizeY, data.sizeZ);
		BuildConfig config = BuildModule.config();
		int depth = 0;
		if (box[1] - depth < level.getMinY() || box[4] >= level.getMaxY()) {
			error[0] = "build.error.height";
			args[0] = new Object[] {level.getMinY(), level.getMaxY()};
			return;
		}
		int outside = 0;
		for (int cx = box[0] >> 4; cx <= box[3] >> 4; cx++) {
			for (int cz = box[2] >> 4; cz <= box[5] >> 4; cz++) {
				Guild owner = GuildManager.atChunk(ChunkKey.of(cx, cz));
				if (owner != null && owner != guild) {
					error[0] = "build.error.foreign";
					args[0] = new Object[] {owner.name};
					return;
				}
				if (owner != guild) {
					outside++;
				}
			}
		}
		if (outside > 0 && config.projects.insideTerritory) {
			error[0] = "build.error.outside";
			args[0] = new Object[] {outside};
			return;
		}
		for (BuildProject other : guild.builds.projects) {
			if (other.id != ignore && !other.finished && other.overlaps(box)) {
				error[0] = "build.error.overlap";
				args[0] = new Object[] {other.name};
				return;
			}
		}
		for (Guild any : GuildManager.all()) {
			if (any != guild) {
				for (BuildProject other : any.builds.projects) {
					if (!other.finished && other.overlaps(box)) {
						error[0] = "build.error.overlap_foreign";
						return;
					}
				}
			}
			if (any.heart != null && within(any.heart, box, 1)) {
				error[0] = "build.error.heart";
				args[0] = new Object[] {any.heart.getX(), any.heart.getY(), any.heart.getZ()};
				return;
			}
			for (WorkZone zone : any.villagers.zones) {
				if (zone.fanion && zone.pos != null && within(zone.pos, box, 0)) {
					error[0] = "build.error.fanion";
					args[0] = new Object[] {zone.name};
					return;
				}
			}
			for (BuiltStructure structure : any.structures.built) {
				if (structure.blocks != null && structure.blocks.stream().anyMatch(pos -> within(pos, box, 0))
						|| structure.signs != null && structure.signs.stream().anyMatch(pos -> within(pos, box, 0))) {
					error[0] = "build.error.structure";
					args[0] = new Object[] {Lang.strip(dev.guildy.structure.StructureManager.displayName(structure.type).getString())};
					return;
				}
			}
		}
		if (ignore < 0 && guild.builds.activeCount() >= config.maxActive(guild.level)) {
			error[0] = "build.error.limit_short";
			args[0] = new Object[] {config.maxActive(guild.level)};
		}
	}

	private static boolean within(BlockPos pos, int[] box, int margin) {
		return pos.getX() >= box[0] - margin && pos.getX() <= box[3] + margin && pos.getY() >= box[1] - margin && pos.getY() <= box[4] + margin
				&& pos.getZ() >= box[2] - margin && pos.getZ() <= box[5] + margin;
	}

	// ================================================================ confirm

	/** Creates the site of the player's preview. @return the new project, or null (message sent) */
	static @Nullable BuildProject confirm(ServerPlayer player, Guild guild) {
		BuildPreview.Session session = BuildPreview.session(player);
		if (session == null) {
			Msg.send(player, "build.preview.none");
			return null;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS) || GuildManager.of(player) != guild) {
			Msg.send(player, "error.no_permission");
			return null;
		}
		BuildPlacement.validate(player, session);
		if (session.error != null) {
			Msg.send(player, "build.error.refused", Lang.raw(session.error, session.errorArgs));
			return null;
		}
		SchematicLibrary.Entry entry = SchematicLibrary.find(session.entryKey);
		MinecraftServer server = GuildyServer.server();
		if (entry == null || server == null) {
			Msg.send(player, "build.error.schematic_gone");
			return null;
		}
		BuildProject project = create(guild, entry, session.plan, session.transform, player.getName().getString());
		BuildSite site = BuildSites.start(guild, project, session.plan);
		SchematicLibrary.copyForSite(server, entry, project.file, copied -> {
			if (copied == null) {
				site.fail("build.error.copy_failed");
				Msg.guild(guild, "build.copy_failed", project.name);
			}
		});
		BuildPreview.end(player);
		removePlans(player);
		Msg.guild(guild, "build.created", player.getName().getString(), project.name, project.sizeX, project.sizeY, project.sizeZ);
		Msg.sound(player, SoundEvents.VILLAGER_WORK_MASON, 1.0F, 1.0F);
		return project;
	}

	/** The project of a new site (also used by the admin command and the tests). */
	static BuildProject create(Guild guild, SchematicLibrary.Entry entry, BuildPlan plan, PlacementTransform transform, String creator) {
		BuildConfig config = BuildModule.config();
		BuildProject project = new BuildProject();
		project.id = guild.builds.nextId++;
		project.name = uniqueName(guild, displayName(entry));
		project.source = entry.key();
		project.format = plan.data.format;
		project.file = SchematicLibrary.copyName(guild.id, project.id, entry);
		project.sizeX = plan.data.sizeX;
		project.sizeY = plan.data.sizeY;
		project.sizeZ = plan.data.sizeZ;
		project.setTransform(transform);
		project.interior = config.projects.defaultInterior;
		project.sources = config.projects.defaultSources;
		project.replaceBuilt = config.projects.defaultReplaceBuilt;
		project.foundations = config.projects.defaultFoundations;
		project.createdAt = System.currentTimeMillis();
		project.createdBy = creator;
		guild.builds.projects.add(project);
		GuildManager.markDirty();
		return project;
	}

	static String uniqueName(Guild guild, String base) {
		String name = base.length() > 32 ? base.substring(0, 32).trim() : base;
		java.util.Set<String> names = guild.builds.names();
		if (!names.contains(name.toLowerCase(Locale.ROOT))) {
			return name;
		}
		for (int i = 2; i < 1000; i++) {
			String candidate = name + " " + i;
			if (!names.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
		return name + " " + System.currentTimeMillis() % 10000;
	}

	/** Small helper: error sound without importing the menus. */
	static final class GuiErrors {
		private GuiErrors() {
		}

		static void sound(ServerPlayer player) {
			dev.guildy.gui.GuiUtil.errorSound(player);
		}
	}
}
