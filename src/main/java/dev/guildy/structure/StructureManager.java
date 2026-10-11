package dev.guildy.structure;

import dev.guildy.Guildy;
import dev.guildy.config.ConfigHandle;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multiblock structures: validation with the plan item, managed signs (placement, protection, text refresh),
 * integrity checks, removal. Every structure lives in the claim dimension, inside its guild's territory.
 */
public final class StructureManager {
	/** Why a structure disappeared (drives the guild notification). */
	public enum RemoveReason {
		BROKEN, INVALID, TERRITORY, DISMANTLED, DISBANDED, ADMIN
	}

	private static final int USE_DEBOUNCE_TICKS = 5;

	private static ConfigHandle<StructureConfig> config;
	/** Last text written on each managed sign (pos.asLong()): signs are only updated when their text changes. */
	private static final Map<Long, List<Component>> SIGN_CACHE = new HashMap<>();
	/** Guilds whose signs must be refreshed soon (quest progress...). */
	private static final Set<UUID> REFRESH_REQUESTS = new LinkedHashSet<>();
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();
	/** Signs taken down before a structure block is broken (avoids the sign item drop), restored if cancelled. */
	private static final Map<Long, PendingBreak> PENDING_BREAKS = new HashMap<>();
	/** Signs of removed structures whose chunk was not loaded: taken down once the chunk is loaded. */
	private static final Set<Long> PENDING_SIGN_REMOVALS = new LinkedHashSet<>();
	private static final int PENDING_REMOVALS_PER_SECOND = 64;
	private static final Set<String> WARNED_TYPES = new HashSet<>();

	private record PendingBreak(Guild guild, BuiltStructure structure, int signIndex, long tick) {
	}

	private StructureManager() {
	}

	static void init(ConfigHandle<StructureConfig> handle) {
		config = handle;
		UseBlockCallback.EVENT.register(StructureManager::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(StructureManager::beforeBreak);
		PlayerBlockBreakEvents.AFTER.register(StructureManager::afterBreak);
		PlayerBlockBreakEvents.CANCELED.register(StructureManager::onBreakCanceled);
		GuildEvents.TERRITORY_CHANGED.add(StructureManager::onTerritoryChanged);
		GuildEvents.DISBANDED.add(StructureManager::onDisbanded);
		Lifecycle.STARTED.add(server -> {
			normalizeAll();
			StructureIndex.rebuild();
			SIGN_CACHE.clear();
		});
		Lifecycle.STOPPING.add(server -> {
			PENDING_BREAKS.clear();
			PENDING_SIGN_REMOVALS.clear();
			REFRESH_REQUESTS.clear();
			LAST_USE.clear();
			SIGN_CACHE.clear();
			StructureIndex.clear();
		});
		Scheduler.every("structure.integrity", 20, 3, StructureManager::tickIntegrity);
		Scheduler.every("structure.signs", 20, 11, StructureManager::tickSigns);
		Scheduler.every("structure.sign_requests", 10, 6, StructureManager::tickRefreshRequests);
	}

	public static StructureConfig config() {
		return config.get();
	}

	// ================================================================ definitions & queries

	public static Collection<StructureType> types() {
		return config.get().types().values();
	}

	public static @Nullable StructureType type(@Nullable String id) {
		return id == null ? null : config.get().types().get(id);
	}

	/** Colorized name of a structure type (the id if unknown). */
	public static Component displayName(String id) {
		StructureType type = type(id);
		return type != null ? type.name() : Component.literal(id);
	}

	public static boolean isUnlocked(Guild guild, StructureDefinition def) {
		return def.unlockCost <= 0 || guild.structures.unlocked.contains(def.id);
	}

	public static List<BuiltStructure> builtOf(Guild guild, String type) {
		List<BuiltStructure> list = new ArrayList<>();
		for (BuiltStructure structure : guild.structures.built) {
			if (type.equals(structure.type)) {
				list.add(structure);
			}
		}
		return list;
	}

	public static boolean isBuilt(Guild guild, String type) {
		return guild.structures.find(type) != null;
	}

	/** Asks for a refresh of the guild's managed signs within half a second (e.g. after quest progress). */
	public static void requestSignRefresh(Guild guild) {
		REFRESH_REQUESTS.add(guild.id);
	}

	// ================================================================ interactions

	private static InteractionResult onUseBlock(Player p, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(p instanceof ServerPlayer player) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		return Safe.get("structure.use", () -> handleUse(player, (ServerLevel) level, hand, hit), InteractionResult.PASS);
	}

	private static InteractionResult handleUse(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (GuildyServer.isClaimLevel(level)) {
			StructureIndex.Ref ref = StructureIndex.sign(pos);
			if (ref != null) {
				// Never let vanilla open the sign editor or apply dyes/wax on a managed sign.
				if (debounce(player)) {
					useSign(player, ref, pos);
				}
				return InteractionResult.SUCCESS;
			}
		}
		ItemStack stack = player.getItemInHand(hand);
		if (GuildyItems.is(stack, GuildyItems.KIND_PLAN)) {
			if (debounce(player)) {
				validate(player, level, pos, hit.getDirection(), GuildyItems.data(stack).getStringOr("structure", ""));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static boolean debounce(ServerPlayer player) {
		long now = Scheduler.ticks();
		Long last = LAST_USE.get(player.getUUID());
		if (last != null && now - last < USE_DEBOUNCE_TICKS && now >= last) {
			return false;
		}
		LAST_USE.put(player.getUUID(), now);
		return true;
	}

	private static void useSign(ServerPlayer player, StructureIndex.Ref ref, BlockPos pos) {
		Guild guild = ref.guild();
		BuiltStructure structure = ref.structure();
		if (!guild.isMember(player.getUUID())) {
			Msg.send(player, "structure.sign.foreign", guild.name);
			GuiUtil.errorSound(player);
			return;
		}
		int index = structure.signIndex(pos);
		StructureBehavior behavior = StructureBehavior.get(structure.type);
		if (behavior != null) {
			behavior.onUse(player, guild, structure, index);
			return;
		}
		StructureType type = type(structure.type);
		if (type != null) {
			StructureMenus.openDetail(player, guild, type);
		}
	}

	// ================================================================ validation

	/** Right-click with a plan: validates the pattern around the clicked block and builds the structure. */
	static void validate(ServerPlayer player, ServerLevel level, BlockPos clicked, Direction face, String typeId) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			fail(player, "error.no_guild");
			return;
		}
		StructureType type = type(typeId);
		if (type == null) {
			fail(player, "structure.validate.unknown_plan");
			return;
		}
		StructureDefinition def = type.def();
		if (!guild.can(player.getUUID(), GuildPermission.STRUCTURES)) {
			fail(player, "error.no_permission");
			return;
		}
		if (!isUnlocked(guild, def)) {
			fail(player, "structure.validate.locked", type.name());
			return;
		}
		if (guild.level < def.minLevel) {
			fail(player, "structure.validate.level", def.minLevel, type.name());
			return;
		}
		if (builtOf(guild, def.id).size() >= def.maxPerGuild) {
			fail(player, "structure.validate.max", def.maxPerGuild, type.name());
			return;
		}
		if (GuildManager.at(level, clicked) != guild) {
			fail(player, "structure.validate.outside");
			return;
		}
		Direction facing = face.getAxis().isHorizontal() ? face : player.getDirection().getOpposite();
		PatternMatcher.Result result = PatternMatcher.match(type.pattern(), tester(level, type, clicked, facing));
		if (!result.found()) {
			reportMismatch(player, level, type, clicked, facing, result);
			return;
		}
		Component firstError = null;
		for (PatternMatcher.Placement placement : result.matches()) {
			BlockPos origin = offsetPos(clicked, facing, placement.originRight(), placement.originUp(type.height()));
			Component error = checkPlacement(level, guild, type, origin, facing);
			if (error == null) {
				build(player, level, guild, type, origin, facing);
				return;
			}
			if (firstError == null) {
				firstError = error;
			}
		}
		Msg.sendRaw(player, firstError);
		GuiUtil.errorSound(player);
	}

	private static PatternMatcher.CellTester tester(ServerLevel level, StructureType type, BlockPos clicked, Direction facing) {
		return (right, up, key) -> {
			BlockPos pos = offsetPos(clicked, facing, right, up);
			BlockKey blockKey = type.key(key);
			return blockKey != null && level.isLoaded(pos) && blockKey.test(level.getBlockState(pos));
		};
	}

	private static BlockPos offsetPos(BlockPos from, Direction facing, int right, int up) {
		int[] offset = Orientation.offset(right, up, facing.getStepX(), facing.getStepZ());
		return from.offset(offset[0], offset[1], offset[2]);
	}

	private static void reportMismatch(ServerPlayer player, ServerLevel level, StructureType type, BlockPos clicked,
			Direction facing, PatternMatcher.Result result) {
		if (result.best() == null || result.bestMismatches().isEmpty()) {
			fail(player, "structure.validate.no_match", type.name());
		} else {
			PatternMatcher.Mismatch mismatch = result.bestMismatches().get(0);
			BlockPos pos = offsetPos(clicked, facing, mismatch.right(), mismatch.up());
			BlockKey key = type.key(mismatch.key());
			Component expected = key.displayName(type.def().labelFor(mismatch.key()));
			Component found = level.isLoaded(pos) ? level.getBlockState(pos).getBlock().getName() : Lang.get("structure.validate.unloaded_block");
			fail(player, "structure.validate.wrong_block", pos.getX(), pos.getY(), pos.getZ(), expected, found);
			if (result.bestMismatches().size() > 1) {
				Msg.send(player, "structure.validate.more_errors", result.bestMismatches().size() - 1);
			}
		}
		for (Direction other : Direction.Plane.HORIZONTAL) {
			if (other != facing && PatternMatcher.match(type.pattern(), tester(level, type, clicked, other)).found()) {
				Msg.send(player, "structure.validate.hint_front");
				return;
			}
		}
		Msg.send(player, "structure.validate.hint_plan");
	}

	/** Checks territory, overlaps and sign spots of a matched placement. @return the error, or null if buildable. */
	@SuppressWarnings("deprecation") // isSolid() is what WallSignBlock.canSurvive checks
	private static @Nullable Component checkPlacement(ServerLevel level, Guild guild, StructureType type, BlockPos origin, Direction facing) {
		for (BlockPos pos : type.blockPositions(origin, facing)) {
			if (StructureIndex.occupied(pos)) {
				return TextUtil.lang("structure.validate.overlap", pos.getX(), pos.getY(), pos.getZ());
			}
			if (GuildManager.at(level, pos) != guild) {
				return TextUtil.lang("structure.validate.territory", pos.getX(), pos.getY(), pos.getZ());
			}
		}
		List<BlockPos> signs = type.signPositions(origin, facing);
		for (int i = 0; i < signs.size(); i++) {
			BlockPos pos = signs.get(i);
			if (StructureIndex.occupied(pos)) {
				return TextUtil.lang("structure.validate.overlap", pos.getX(), pos.getY(), pos.getZ());
			}
			if (GuildManager.at(level, pos) != guild) {
				return TextUtil.lang("structure.validate.territory", pos.getX(), pos.getY(), pos.getZ());
			}
			BlockPos support = type.signSupport(origin, facing, i);
			if (!level.isLoaded(pos) || !level.isLoaded(support)) {
				return TextUtil.lang("structure.validate.unloaded", pos.getX(), pos.getY(), pos.getZ());
			}
			if (!level.getBlockState(pos).canBeReplaced()) {
				return TextUtil.lang("structure.validate.sign_blocked", pos.getX(), pos.getY(), pos.getZ(),
						level.getBlockState(pos).getBlock().getName());
			}
			BlockState supportState = level.getBlockState(support);
			if (!supportState.isSolid() || !supportState.isFaceSturdy(level, support, facing)) {
				return TextUtil.lang("structure.validate.not_sturdy", support.getX(), support.getY(), support.getZ());
			}
		}
		return null;
	}

	private static void build(ServerPlayer player, ServerLevel level, Guild guild, StructureType type, BlockPos origin, Direction facing) {
		BuiltStructure structure = new BuiltStructure();
		structure.type = type.id();
		structure.origin = origin;
		structure.facing = facing;
		structure.width = type.width();
		structure.height = type.height();
		structure.blocks = new ArrayList<>(type.blockPositions(origin, facing));
		structure.signs = new ArrayList<>(type.signPositions(origin, facing));
		structure.builtAt = System.currentTimeMillis();
		guild.structures.built.add(structure);
		StructureIndex.add(guild, structure);
		GuildManager.markDirty();

		for (int i = 0; i < structure.signs.size(); i++) {
			placeSign(level, structure.signs.get(i), facing, defaultLines(type));
		}
		StructureBehavior behavior = StructureBehavior.get(type.id());
		if (behavior != null) {
			Safe.run("structure.onBuilt." + type.id(), () -> behavior.onBuilt(level, guild, structure, player));
		}
		refreshSigns(guild);

		BlockPos center = structure.blocks.get(structure.blocks.size() / 2);
		Msg.sendRaw(player, TextUtil.lang("structure.built", type.name(), origin.getX(), origin.getY(), origin.getZ()));
		Component announcement = Msg.prefixed(TextUtil.lang("structure.built.guild", player.getName().getString(), type.name()));
		for (ServerPlayer member : guild.onlineMembers()) {
			if (member != player) {
				member.sendSystemMessage(announcement);
			}
		}
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
		for (BlockPos sign : structure.signs) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sign.getX() + 0.5, sign.getY() + 0.5, sign.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 20, 0.8, 0.8, 0.8, 0.02);

		if (guild.structures.tutorialsSeen.add(type.id())) {
			GuildManager.markDirty();
			for (ServerPlayer member : guild.onlineMembers()) {
				for (String line : type.def().tutorial) {
					member.sendSystemMessage(TextUtil.colored(line));
				}
			}
		}
	}

	// ================================================================ removal

	/**
	 * Removes a structure (signs taken down without drops, behavior notified, guild informed).
	 *
	 * @param actor name of the player responsible (dismantle / broken block), may be null
	 * @param at    broken block position for {@link RemoveReason#BROKEN}, may be null
	 */
	public static void remove(Guild guild, BuiltStructure structure, RemoveReason reason, @Nullable String actor, @Nullable BlockPos at) {
		if (!guild.structures.built.remove(structure)) {
			return;
		}
		StructureIndex.remove(structure);
		GuildManager.markDirty();
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null && structure.signs != null) {
			for (BlockPos pos : structure.signs) {
				Safe.run("structure.remove_sign", () -> removeSignBlock(level, pos));
			}
		}
		StructureBehavior behavior = StructureBehavior.get(structure.type);
		if (behavior != null && level != null) {
			Safe.run("structure.onRemoved." + structure.type, () -> behavior.onRemoved(level, guild, structure));
		}
		Component name = displayName(structure.type);
		switch (reason) {
			case BROKEN -> {
				BlockPos pos = at != null ? at : structure.origin;
				Msg.guildRaw(guild, TextUtil.lang("structure.removed.broken", name, actor == null ? "?" : actor,
						pos.getX(), pos.getY(), pos.getZ()));
			}
			case INVALID -> Msg.guildRaw(guild, TextUtil.lang("structure.removed.invalid", name));
			case TERRITORY -> Msg.guildRaw(guild, TextUtil.lang("structure.removed.territory", name));
			case DISMANTLED -> Msg.guildRaw(guild, TextUtil.lang("structure.removed.dismantled", actor == null ? "?" : actor, name));
			case ADMIN -> Msg.guildRaw(guild, TextUtil.lang("structure.removed.admin", name));
			case DISBANDED -> {
			}
		}
	}

	/** Dismantle from the menu (STRUCTURES permission). @return the error, or null on success. */
	public static @Nullable Component dismantle(ServerPlayer player, Guild guild, BuiltStructure structure) {
		if (!guild.can(player.getUUID(), GuildPermission.STRUCTURES)) {
			return Lang.get("error.no_permission");
		}
		if (!guild.structures.built.contains(structure)) {
			return Lang.get("structure.dismantle.gone");
		}
		remove(guild, structure, RemoveReason.DISMANTLED, player.getName().getString(), null);
		return null;
	}

	/** Takes a managed sign down without drop; deferred until its chunk is loaded (never forces a chunk load). */
	private static void removeSignBlock(ServerLevel level, BlockPos pos) {
		SIGN_CACHE.remove(pos.asLong());
		if (!level.isLoaded(pos)) {
			PENDING_SIGN_REMOVALS.add(pos.asLong());
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof SignBlock) {
			level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		}
	}

	private static void processPendingSignRemovals(ServerLevel level) {
		int budget = PENDING_REMOVALS_PER_SECOND;
		var it = PENDING_SIGN_REMOVALS.iterator();
		while (it.hasNext() && budget > 0) {
			BlockPos pos = BlockPos.of(it.next());
			if (StructureIndex.sign(pos) != null) {
				// The spot belongs to a newer structure.
				it.remove();
				continue;
			}
			if (!level.isLoaded(pos)) {
				continue;
			}
			it.remove();
			budget--;
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof SignBlock) {
				level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			}
		}
	}

	// ================================================================ unlock & plans

	/** Unlocks a structure with treasury gold. @return the error, or null on success. */
	public static @Nullable Component unlock(ServerPlayer player, Guild guild, StructureType type) {
		StructureDefinition def = type.def();
		if (!guild.can(player.getUUID(), GuildPermission.STRUCTURES)) {
			return Lang.get("error.no_permission");
		}
		if (isUnlocked(guild, def)) {
			return Lang.get("structure.unlock.already");
		}
		if (guild.level < def.minLevel) {
			return TextUtil.lang("structure.unlock.level", def.minLevel);
		}
		if (!GuildManager.takeTreasury(guild, def.unlockCost)) {
			return Lang.get("error.not_enough_treasury", def.unlockCost);
		}
		guild.structures.unlocked.add(def.id);
		GuildManager.markDirty();
		Msg.guildRaw(guild, TextUtil.lang("structure.unlock.done", player.getName().getString(), type.name(), def.unlockCost));
		return null;
	}

	/** Admin unlock (free, no level check). @return false if it was already unlocked. */
	public static boolean adminUnlock(Guild guild, StructureType type) {
		boolean added = guild.structures.unlocked.add(type.id());
		if (added) {
			GuildManager.markDirty();
			Msg.guildRaw(guild, TextUtil.lang("structure.unlock.admin", type.name()));
		}
		return added;
	}

	/** Gives the plan item. @return the error, or null on success. */
	public static @Nullable Component givePlan(ServerPlayer player, Guild guild, StructureType type) {
		if (!isUnlocked(guild, type.def())) {
			return Lang.get("structure.plan.locked");
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (GuildyItems.is(stack, GuildyItems.KIND_PLAN) && type.id().equals(GuildyItems.data(stack).getStringOr("structure", ""))) {
				return Lang.get("structure.plan.already");
			}
		}
		ItemUtil.giveOrDrop(player, GuildyItems.plan(type.id(), type.name(), planLore(type)));
		return null;
	}

	/** Plan lore: layout as seen from the front, legend, sign count, usage. */
	public static List<Component> planLore(StructureType type) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("structure.plan.lore.layout"));
		for (String row : type.pattern().rows()) {
			StringBuilder sb = new StringBuilder("  ");
			for (int i = 0; i < row.length(); i++) {
				if (i > 0) {
					sb.append(' ');
				}
				sb.append(row.charAt(i));
			}
			lore.add(Lang.get("structure.plan.lore.row", sb.toString()));
		}
		for (char c : type.pattern().keys()) {
			BlockKey key = type.key(c);
			lore.add(TextUtil.lang("structure.plan.lore.key", String.valueOf(c), key.displayName(type.def().labelFor(c))));
		}
		if (type.pattern().rows().stream().anyMatch(r -> r.indexOf(StructurePattern.ANY) >= 0)) {
			lore.add(Lang.get("structure.plan.lore.any"));
		}
		lore.add(Lang.get("structure.plan.lore.signs", type.def().signCount()));
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("structure.plan.lore.usage"));
		return lore;
	}

	// ================================================================ signs

	private static boolean placeSign(ServerLevel level, BlockPos pos, Direction facing, List<Component> lines) {
		Block signBlock = config.get().signBlock();
		BlockState state = signBlock.defaultBlockState();
		if (state.hasProperty(WallSignBlock.FACING)) {
			state = state.setValue(WallSignBlock.FACING, facing);
		}
		if (state.hasProperty(SignBlock.WATERLOGGED)) {
			state = state.setValue(SignBlock.WATERLOGGED, level.getFluidState(pos).isSourceOfType(Fluids.WATER));
		}
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
			return false;
		}
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			applyText(sign, lines);
			sign.setWaxed(true);
			SIGN_CACHE.put(pos.asLong(), lines);
			return true;
		}
		return false;
	}

	private static void applyText(SignBlockEntity sign, List<Component> lines) {
		// setText marks the block entity changed and sends a block update so tracking clients see the text.
		sign.setText(new SignText(lines, lines, DyeColor.BLACK, config.get().glowingSignText), SignTextSlot.FRONT);
	}

	private static List<Component> defaultLines(StructureType type) {
		return normalize(List.of(Component.empty(), TextUtil.lang("structure.sign.default.name", type.name()),
				Lang.get("structure.sign.default.hint"), Component.empty()));
	}

	/** Exactly 4 lines. */
	private static List<Component> normalize(List<Component> lines) {
		List<Component> out = new ArrayList<>(4);
		for (int i = 0; i < 4; i++) {
			Component line = i < lines.size() ? lines.get(i) : null;
			out.add(line == null ? Component.empty() : line);
		}
		return List.copyOf(out);
	}

	/** Text of sign {@code index} from the structure behavior; null = leave the sign unchanged. */
	private static @Nullable List<Component> linesFor(Guild guild, BuiltStructure structure, int index) {
		StructureBehavior behavior = StructureBehavior.get(structure.type);
		if (behavior == null) {
			return null;
		}
		List<Component> lines = Safe.get("structure.signText." + structure.type, () -> behavior.signText(guild, structure, index), null);
		return lines == null ? null : normalize(lines);
	}

	/** Updates the text of the guild's managed signs in loaded chunks (only signs whose text changed). */
	public static void refreshSigns(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (BuiltStructure structure : guild.structures.built) {
			if (structure.signs == null) {
				continue;
			}
			for (int i = 0; i < structure.signs.size(); i++) {
				BlockPos pos = structure.signs.get(i);
				if (!level.isLoaded(pos)) {
					continue;
				}
				List<Component> lines = linesFor(guild, structure, i);
				if (lines == null || lines.equals(SIGN_CACHE.get(pos.asLong()))) {
					continue;
				}
				if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
					applyText(sign, lines);
					SIGN_CACHE.put(pos.asLong(), lines);
				}
			}
		}
	}

	private static void tickSigns() {
		int interval = config.get().signRefreshSeconds;
		long slice = (Scheduler.ticks() / 20) % interval;
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (!guild.structures.built.isEmpty() && Math.floorMod(guild.id.hashCode(), interval) == slice) {
				Safe.run("structure.signs", () -> refreshSigns(guild));
			}
		}
	}

	private static void tickRefreshRequests() {
		if (REFRESH_REQUESTS.isEmpty()) {
			return;
		}
		List<UUID> ids = new ArrayList<>(REFRESH_REQUESTS);
		REFRESH_REQUESTS.clear();
		for (UUID id : ids) {
			Guild guild = GuildManager.get(id);
			if (guild != null) {
				Safe.run("structure.signs", () -> refreshSigns(guild));
			}
		}
	}

	private static void resyncSign(ServerPlayer player, ServerLevel level, BlockPos pos) {
		player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			player.connection.send(sign.getUpdatePacket());
		}
	}

	// ================================================================ block breaking

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !GuildyServer.isClaimLevel(level)) {
			return true;
		}
		return Safe.get("structure.before_break", () -> handleBeforeBreak((ServerLevel) level, player, pos), true);
	}

	private static boolean handleBeforeBreak(ServerLevel level, Player player, BlockPos pos) {
		if (StructureIndex.sign(pos) != null) {
			if (player instanceof ServerPlayer serverPlayer) {
				Msg.send(serverPlayer, "structure.sign.protected");
				// The client predicted the break: send the sign (and its text) back.
				Scheduler.later("structure.sign_resync", 1, () -> resyncSign(serverPlayer, level, pos));
			}
			return false;
		}
		StructureIndex.Ref ref = StructureIndex.block(pos);
		if (ref != null && ref.structure().facing != null) {
			BuiltStructure structure = ref.structure();
			BlockPos front = pos.relative(structure.facing);
			int index = structure.signIndex(front);
			if (index >= 0) {
				// Take the sign down now, otherwise it pops off and drops a free sign item.
				removeSignBlock(level, front);
				PENDING_BREAKS.put(pos.asLong(), new PendingBreak(ref.guild(), structure, index, Scheduler.ticks()));
			}
		}
		return true;
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !GuildyServer.isClaimLevel(level)) {
			return;
		}
		Safe.run("structure.after_break", () -> {
			PENDING_BREAKS.remove(pos.asLong());
			StructureIndex.Ref ref = StructureIndex.block(pos);
			if (ref != null) {
				remove(ref.guild(), ref.structure(), RemoveReason.BROKEN, player.getName().getString(), pos);
			}
		});
	}

	private static void onBreakCanceled(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide()) {
			return;
		}
		Safe.run("structure.break_canceled", () -> {
			PendingBreak pending = PENDING_BREAKS.remove(pos.asLong());
			if (pending != null) {
				restoreSign((ServerLevel) level, pending);
			}
		});
	}

	private static void restoreSign(ServerLevel level, PendingBreak pending) {
		BuiltStructure structure = pending.structure();
		if (!pending.guild().structures.built.contains(structure) || pending.signIndex() >= structure.signs.size()) {
			return;
		}
		BlockPos pos = structure.signs.get(pending.signIndex());
		if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced()) {
			StructureType type = type(structure.type);
			List<Component> lines = linesFor(pending.guild(), structure, pending.signIndex());
			if (lines == null) {
				lines = type != null ? defaultLines(type) : normalize(List.of());
			}
			placeSign(level, pos, structure.facing, lines);
		}
	}

	// ================================================================ integrity

	private static void tickIntegrity() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		if (!PENDING_SIGN_REMOVALS.isEmpty()) {
			Safe.run("structure.pending_signs", () -> processPendingSignRemovals(level));
		}
		if (!PENDING_BREAKS.isEmpty()) {
			// A break that neither completed nor was cancelled (should not happen): put the sign back.
			long now = Scheduler.ticks();
			for (var entry : List.copyOf(PENDING_BREAKS.entrySet())) {
				if (now - entry.getValue().tick() > 2) {
					PENDING_BREAKS.remove(entry.getKey());
					Safe.run("structure.pending", () -> restoreSign(level, entry.getValue()));
				}
			}
		}
		int interval = config.get().checkIntervalSeconds;
		long slice = (Scheduler.ticks() / 20) % interval;
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (!guild.structures.built.isEmpty() && Math.floorMod(guild.id.hashCode(), interval) == slice) {
				Safe.run("structure.check", () -> checkGuild(level, guild));
			}
		}
	}

	/** Re-checks every loaded structure of the guild; broken ones are removed. */
	public static void checkGuild(ServerLevel level, Guild guild) {
		for (BuiltStructure structure : List.copyOf(guild.structures.built)) {
			if (!isLoaded(level, structure)) {
				continue;
			}
			RemoveReason reason = integrity(level, guild, structure);
			if (reason != null) {
				remove(guild, structure, reason, null, null);
			}
		}
	}

	private static boolean isLoaded(ServerLevel level, BuiltStructure structure) {
		if (structure.blocks == null || structure.signs == null) {
			return false;
		}
		for (BlockPos pos : structure.blocks) {
			if (!level.isLoaded(pos)) {
				return false;
			}
		}
		for (BlockPos pos : structure.signs) {
			if (!level.isLoaded(pos)) {
				return false;
			}
		}
		return true;
	}

	/** @return why the structure is no longer valid, or null when intact (unknown types are left alone). */
	private static @Nullable RemoveReason integrity(ServerLevel level, Guild guild, BuiltStructure structure) {
		StructureType type = type(structure.type);
		if (type == null) {
			if (WARNED_TYPES.add(String.valueOf(structure.type))) {
				Guildy.LOGGER.warn("Structure type '{}' is not defined in structures.json5: existing ones are kept unchecked "
						+ "(breaking one of their blocks removes them)", structure.type);
			}
			return null;
		}
		if (structure.origin == null || structure.facing == null || !structure.facing.getAxis().isHorizontal()
				|| type.width() != structure.width || type.height() != structure.height
				|| !type.blockPositions(structure.origin, structure.facing).equals(structure.blocks)
				|| !type.signPositions(structure.origin, structure.facing).equals(structure.signs)) {
			return RemoveReason.INVALID;
		}
		StructurePattern pattern = type.pattern();
		for (int row = 0; row < pattern.height(); row++) {
			for (int col = 0; col < pattern.width(); col++) {
				if (!pattern.isWildcard(col, row)
						&& !type.key(pattern.at(col, row)).test(level.getBlockState(type.cellPos(structure.origin, structure.facing, col, row)))) {
					return RemoveReason.INVALID;
				}
			}
		}
		for (BlockPos sign : structure.signs) {
			if (!(level.getBlockState(sign).getBlock() instanceof SignBlock)) {
				return RemoveReason.INVALID;
			}
		}
		return insideTerritory(level, guild, structure) ? null : RemoveReason.TERRITORY;
	}

	private static boolean insideTerritory(ServerLevel level, Guild guild, BuiltStructure structure) {
		if (structure.blocks != null) {
			for (BlockPos pos : structure.blocks) {
				if (GuildManager.at(level, pos) != guild) {
					return false;
				}
			}
		}
		if (structure.signs != null) {
			for (BlockPos pos : structure.signs) {
				if (GuildManager.at(level, pos) != guild) {
					return false;
				}
			}
		}
		return true;
	}

	// ================================================================ guild events

	private static void onTerritoryChanged(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (BuiltStructure structure : List.copyOf(guild.structures.built)) {
			if (!insideTerritory(level, guild, structure)) {
				remove(guild, structure, RemoveReason.TERRITORY, null, null);
			}
		}
	}

	private static void onDisbanded(Guild guild) {
		for (BuiltStructure structure : List.copyOf(guild.structures.built)) {
			remove(guild, structure, RemoveReason.DISBANDED, null, null);
		}
	}

	/** Fixes data loaded from older/hand-edited files. */
	private static void normalizeAll() {
		for (Guild guild : GuildManager.all()) {
			GuildStructureData data = guild.structures;
			if (data.unlocked == null) {
				data.unlocked = new LinkedHashSet<>();
			}
			if (data.tutorialsSeen == null) {
				data.tutorialsSeen = new LinkedHashSet<>();
			}
			if (data.built == null) {
				data.built = new ArrayList<>();
			}
			data.built.removeIf(s -> s == null || s.type == null || s.origin == null || s.facing == null);
			for (BuiltStructure structure : data.built) {
				StructureType type = type(structure.type);
				if (structure.blocks == null) {
					structure.blocks = type != null ? new ArrayList<>(type.blockPositions(structure.origin, structure.facing)) : new ArrayList<>();
				}
				if (structure.signs == null) {
					structure.signs = type != null ? new ArrayList<>(type.signPositions(structure.origin, structure.facing)) : new ArrayList<>();
				}
			}
		}
	}

	private static void fail(ServerPlayer player, String key, Object... args) {
		Msg.sendRaw(player, TextUtil.lang(key, args));
		GuiUtil.errorSound(player);
	}
}
