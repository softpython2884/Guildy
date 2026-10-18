package dev.guildy.villager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chat commands of the guild storage (permission: managing the villagers), under {@code /guild villagers storage} and
 * its short form {@code /guild stockage}:
 * <ul>
 * <li>{@code scan <chunks> [remplacer]}: analyses the containers around the player ({@link ChestScan}), then
 * {@code scan apply|cancel [id]} and {@code scan show <id> [index]} (the clickable chat buttons);</li>
 * <li>{@code filter here|ici}: the container the player looks at becomes a filter chest inferred from its contents;
 * {@code filter clear|retirer}: the filter chest leaves the storage;</li>
 * <li>{@code layout [colonnes|lignes]}: the layout inside the chests of the guild.</li>
 * </ul>
 */
final class StorageCommands {
	/** Reach of "the container you look at" (blocks). */
	private static final double LOOK_RANGE = 6.0;

	private StorageCommands() {
	}

	static void init() {
		GuildCommand.addSubcommands((root, context) -> {
			root.then(Commands.literal("villagers").then(tree("storage")));
			root.then(tree("stockage"));
		});
	}

	private static int run(CommandContext<CommandSourceStack> ctx, String name, Consumer<ServerPlayer> action) throws CommandSyntaxException {
		ServerPlayer player = GuildCommand.player(ctx);
		Safe.run("villager.command.storage." + name, () -> action.accept(player));
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tree(String literal) {
		LiteralArgumentBuilder<CommandSourceStack> scan = Commands.literal("scan")
				.executes(ctx -> run(ctx, "scan", player -> Msg.send(player, "villager.storage.scan.usage", VillagerModule.config().storage.scanMaxChunks)))
				.then(Commands.argument("chunks", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> run(ctx, "scan", player -> ChestScan.start(player, IntegerArgumentType.getInteger(ctx, "chunks"), false)))
						.then(Commands.literal("remplacer")
								.executes(ctx -> run(ctx, "scan", player -> ChestScan.start(player, IntegerArgumentType.getInteger(ctx, "chunks"), true))))
						.then(Commands.literal("replace")
								.executes(ctx -> run(ctx, "scan", player -> ChestScan.start(player, IntegerArgumentType.getInteger(ctx, "chunks"), true)))));
		for (String apply : List.of("apply", "appliquer")) {
			scan.then(Commands.literal(apply)
					.executes(ctx -> run(ctx, "scan.apply", player -> ChestScan.apply(player, -1)))
					.then(Commands.argument("id", IntegerArgumentType.integer(0))
							.executes(ctx -> run(ctx, "scan.apply", player -> ChestScan.apply(player, IntegerArgumentType.getInteger(ctx, "id"))))));
		}
		for (String cancel : List.of("cancel", "annuler")) {
			scan.then(Commands.literal(cancel)
					.executes(ctx -> run(ctx, "scan.cancel", player -> ChestScan.cancel(player, -1)))
					.then(Commands.argument("id", IntegerArgumentType.integer(0))
							.executes(ctx -> run(ctx, "scan.cancel", player -> ChestScan.cancel(player, IntegerArgumentType.getInteger(ctx, "id"))))));
		}
		scan.then(Commands.literal("show")
				.then(Commands.argument("id", IntegerArgumentType.integer(0))
						.executes(ctx -> run(ctx, "scan.show", player -> ChestScan.show(player, IntegerArgumentType.getInteger(ctx, "id"), -1)))
						.then(Commands.argument("index", IntegerArgumentType.integer(0))
								.executes(ctx -> run(ctx, "scan.show", player -> ChestScan.show(player, IntegerArgumentType.getInteger(ctx, "id"),
										IntegerArgumentType.getInteger(ctx, "index")))))));

		LiteralArgumentBuilder<CommandSourceStack> filter = Commands.literal("filter");
		for (String here : List.of("here", "ici")) {
			filter.then(Commands.literal(here).executes(ctx -> run(ctx, "filter.here", StorageCommands::filterHere)));
		}
		for (String clear : List.of("clear", "retirer")) {
			filter.then(Commands.literal(clear).executes(ctx -> run(ctx, "filter.clear", StorageCommands::filterClear)));
		}
		LiteralArgumentBuilder<CommandSourceStack> filtre = Commands.literal("filtre");
		filtre.then(Commands.literal("ici").executes(ctx -> run(ctx, "filter.here", StorageCommands::filterHere)));
		filtre.then(Commands.literal("retirer").executes(ctx -> run(ctx, "filter.clear", StorageCommands::filterClear)));

		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal)
				.executes(ctx -> run(ctx, "help", player -> Msg.send(player, "villager.storage.commands.help")))
				.then(scan).then(filter).then(filtre);
		for (String layout : List.of("layout", "rangement")) {
			node.then(Commands.literal(layout)
					.executes(ctx -> run(ctx, "layout", StorageCommands::showLayout))
					.then(Commands.argument("mode", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("colonnes", "lignes"), builder))
							.executes(ctx -> run(ctx, "layout", player -> setLayout(player, StringArgumentType.getString(ctx, "mode"))))));
		}
		return node;
	}

	// ================================================================ helpers

	/** The player's guild when the player may manage its villagers (messages otherwise). */
	private static @Nullable Guild permitted(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
			return null;
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			return null;
		}
		return guild;
	}

	/** The storage container of the guild territory the player looks at (6 blocks), or null (message). */
	private static @Nullable BlockPos lookedAt(ServerPlayer player, ServerLevel level, Guild guild) {
		HitResult hit = player.pick(LOOK_RANGE, 1.0F, false);
		if (player.level() == level && hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = block.getBlockPos();
			if (Storage.isStorageBlock(level.getBlockEntity(pos)) && GuildManager.at(level, pos) == guild) {
				return pos.immutable();
			}
		}
		Msg.send(player, "villager.storage.filter.no_target");
		return null;
	}

	private static void filterHere(ServerPlayer player) {
		Guild guild = permitted(player);
		ServerLevel level = GuildyServer.claimLevel();
		if (guild == null || level == null) {
			return;
		}
		BlockPos pos = lookedAt(player, level, guild);
		if (pos != null) {
			setFromContents(player, guild, level, pos);
		}
	}

	/**
	 * Makes the container a filter chest accepting what it holds (family or exact items; registered if needed, its
	 * previous role replaced); messages to the player.
	 *
	 * @return true when done
	 */
	static boolean setFromContents(ServerPlayer player, Guild guild, ServerLevel level, BlockPos pos) {
		VillagerConfig.Storage config = VillagerModule.config().storage;
		if (config.maxFilterChests <= 0) {
			Msg.send(player, "villager.storage.filter.disabled");
			return false;
		}
		Container container = Storage.container(level, pos);
		if (container == null || GuildManager.at(level, pos) != guild) {
			Msg.send(player, "villager.storage.filter.no_target");
			return false;
		}
		List<ChestScan.ItemSeen> items = ChestScan.contents(container);
		if (items.isEmpty()) {
			Msg.send(player, "villager.storage.filter.empty");
			return false;
		}
		List<String> ids = new ArrayList<>(items.size());
		for (ChestScan.ItemSeen item : items) {
			ids.add(Storage.itemId(item.prototype()));
		}
		ChestFilter filter = ChestFilters.infer(ids, config.filterMaxItems);
		if (filter == null) {
			Msg.send(player, "villager.storage.filter.too_mixed", ids.size(), config.filterMaxItems);
			return false;
		}
		StorageChest chest = Storage.registered(level, guild, pos);
		if ((chest == null || chest.role != StorageChest.Role.FILTER) && ChestScan.filterCount(guild) >= config.maxFilterChests) {
			Msg.send(player, "villager.storage.filter.limit", config.maxFilterChests);
			return false;
		}
		if (chest == null) {
			chest = new StorageChest(pos, StorageChest.Role.FILTER, "");
			ChestRoom room = ChestScan.roomAt(guild, pos);
			chest.room = room == null ? 0 : room.id;
			guild.villagers.chests.add(chest);
		}
		chest.role = StorageChest.Role.FILTER;
		chest.filter = filter;
		chest.category = "";
		chest.auto = false;
		// Never over a name given by a player.
		StorageLabels.sync(level, guild, chest);
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		Msg.sendRaw(player, Lang.get("villager.storage.filter.set").append(FilterNames.styled(filter, ChatFormatting.LIGHT_PURPLE))
				.append(Lang.get("villager.storage.filter.set.detail", FilterNames.description(filter))));
		return true;
	}

	private static void filterClear(ServerPlayer player) {
		Guild guild = permitted(player);
		ServerLevel level = GuildyServer.claimLevel();
		if (guild == null || level == null) {
			return;
		}
		BlockPos pos = lookedAt(player, level, guild);
		if (pos == null) {
			return;
		}
		StorageChest chest = Storage.registered(level, guild, pos);
		if (chest == null || chest.role != StorageChest.Role.FILTER) {
			Msg.send(player, "villager.storage.filter.none");
			return;
		}
		StorageLabels.clear(level, chest);
		guild.villagers.chests.remove(chest);
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		Msg.send(player, "villager.storage.filter.cleared");
	}

	private static void showLayout(ServerPlayer player) {
		Guild guild = permitted(player);
		if (guild != null) {
			Msg.send(player, "villager.storage.layout.current", layoutName(StorageGroups.fill(guild)));
		}
	}

	private static void setLayout(ServerPlayer player, String value) {
		Guild guild = permitted(player);
		if (guild == null) {
			return;
		}
		StorageLayout.Fill fill = StorageLayout.Fill.parse(value);
		if (fill == null) {
			Msg.send(player, "villager.storage.layout.unknown", value);
			return;
		}
		setLayout(guild, fill, message -> Msg.send(player, "villager.storage.layout.changed", message));
	}

	/** Sets the layout inside the chests of the guild; the sorters check every group again. */
	static void setLayout(Guild guild, StorageLayout.Fill fill, Consumer<String> done) {
		guild.villagers.chestLayout = fill.id();
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		done.accept(layoutName(fill));
	}

	/** "par colonnes" / "par lignes". */
	static String layoutName(StorageLayout.Fill fill) {
		return Lang.raw(fill == StorageLayout.Fill.COLUMNS ? "villager.storage.layout.columns" : "villager.storage.layout.rows");
	}
}
