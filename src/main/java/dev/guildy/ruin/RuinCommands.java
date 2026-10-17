package dev.guildy.ruin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.ruin.GuildRuinData.Research;
import dev.guildy.ruin.RuinConfig.RuinDef;
import dev.guildy.structure.GuildArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /guild admin ruin list | info | awaken | reset | locate <id> | map <player> <id> | incantation <player> [count] |
 * ability <type> [style] | research <guild> finish} (the admin node is already permission-gated). "Nearest" = nearest
 * known ruin in the dimension of the command source. {@code awaken} is instant (no scroll, no ritual).
 */
final class RuinCommands {
	private static final int LIST_MAX = 40;
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.ruins", RuinCommands::ids, List.of()), builder);

	private RuinCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("ruin")
				.then(Commands.literal("list").executes(ctx -> Safe.get("ruin.admin.list", () -> list(ctx), 0)))
				.then(Commands.literal("info").executes(ctx -> Safe.get("ruin.admin.info", () -> info(ctx), 0)))
				.then(Commands.literal("awaken").executes(ctx -> Safe.get("ruin.admin.awaken", () -> awaken(ctx), 0)))
				.then(Commands.literal("reset").executes(ctx -> Safe.get("ruin.admin.reset", () -> reset(ctx), 0)))
				.then(Commands.literal("locate")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
								.executes(ctx -> Safe.get("ruin.admin.locate", () -> locate(ctx), 0))))
				.then(Commands.literal("map")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(RuinCommands::map))))
				.then(Commands.literal("incantation")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> incantation(ctx, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
										.executes(ctx -> incantation(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("ability")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RuinConfig.ABILITIES, builder))
								.executes(ctx -> Safe.get("ruin.admin.ability", () -> ability(ctx, ""), 0))
								.then(Commands.argument("style", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RuinConfig.STYLES, builder))
										.executes(ctx -> Safe.get("ruin.admin.ability", () -> ability(ctx, StringArgumentType.getString(ctx, "style")), 0)))))
				.then(Commands.literal("research")
						.then(GuildArgument.guild("guild")
								.then(Commands.literal("finish").executes(ctx -> Safe.get("ruin.admin.research", () -> finish(ctx), 0)))))));
	}

	private static List<String> ids() {
		Set<String> ids = new LinkedHashSet<>(RuinModule.config().ruins.keySet());
		for (RuinRecord ruin : RuinIndex.all()) {
			ids.add(ruin.ruinId);
		}
		return new ArrayList<>(ids);
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<RuinRecord> ruins = new ArrayList<>(RuinIndex.all());
		source.sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.list.header", ruins.size(), Encounters.count())), false);
		MinecraftServer server = source.getServer();
		long today = Encounters.day(server);
		int shown = 0;
		for (RuinRecord ruin : ruins) {
			if (shown++ >= LIST_MAX) {
				int more = ruins.size() - LIST_MAX;
				source.sendSuccess(() -> Lang.get("ruin.admin.list.more", more), false);
				break;
			}
			source.sendSuccess(() -> line(ruin, today), false);
		}
		return ruins.size();
	}

	private static Component line(RuinRecord ruin, long today) {
		RuinDef def = Encounters.def(ruin);
		BlockPos at = ruin.target();
		MutableComponent text = Lang.get("ruin.admin.list.line", Lang.colorize(def.name), ruin.ruinId, dimensionName(ruin),
				at.getX(), at.getY(), at.getZ(), AtelierMenu.stateLine(ruin, def, today).getString());
		return text.withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand("/execute in " + ruin.dimension + " run tp @s " + at.getX() + " " + (at.getY() + 1) + " " + at.getZ()))
				.withHoverEvent(new HoverEvent.ShowText(Lang.get("ruin.admin.list.hover"))));
	}

	private static @Nullable RuinRecord nearest(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Vec3 pos = source.getPosition();
		RuinRecord ruin = RuinIndex.nearest(source.getLevel().dimension(), pos.x, pos.z, null);
		if (ruin == null) {
			source.sendFailure(Msg.prefixed(Lang.get("ruin.admin.none")));
		}
		return ruin;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		CommandSourceStack source = ctx.getSource();
		RuinDef def = Encounters.def(ruin);
		long today = Encounters.day(source.getServer());
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("ruin.admin.info.header", Lang.colorize(def.name), ruin.ruinId, RuinModule.config().isKnown(ruin.ruinId)
				? Lang.raw("ruin.admin.info.configured") : Lang.raw("ruin.admin.info.generic")));
		lines.add(Lang.get("ruin.admin.info.structure", ruin.structure, dimensionName(ruin)));
		lines.add(Lang.get("ruin.admin.info.box", ruin.minX, ruin.minY, ruin.minZ, ruin.maxX, ruin.maxY, ruin.maxZ));
		lines.add(Lang.get("ruin.admin.info.markers", pos(ruin.boss), ruin.minions.size(), pos(ruin.entrance)));
		lines.add(Lang.get("ruin.admin.info.state", AtelierMenu.stateLine(ruin, def, today).getString(), ruin.timesCleared));
		double ritual = Incantations.ritualProgress(ruin);
		if (ritual >= 0) {
			lines.add(Lang.get("ruin.admin.info.ritual", (int) Math.round(ritual * 100), Incantations.ritualParticipants(ruin).size()));
		}
		Mob boss = Encounters.boss(ruin);
		if (boss != null) {
			lines.add(Lang.get("ruin.admin.info.fight", Lang.colorize(def.boss.name), (int) Math.ceil(boss.getHealth()), (int) Math.ceil(boss.getMaxHealth()),
					Encounters.minions(ruin).size(), Encounters.barViewers(ruin).size()));
		}
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int awaken(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		String error = Encounters.awaken(ctx.getSource().getLevel(), ruin, null, true);
		if (error != null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get(error)));
			return 0;
		}
		RuinDef def = Encounters.def(ruin);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.awakened", Lang.colorize(def.boss.name), Lang.colorize(def.name))), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		Encounters.reset(ruin);
		RuinDef def = Encounters.def(ruin);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.reset", Lang.colorize(def.name))), true);
		return 1;
	}

	private static int locate(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		CommandSourceStack source = ctx.getSource();
		RuinRecord ruin = find(source.getServer(), source.getLevel(), BlockPos.containing(source.getPosition()), id, false);
		if (ruin == null) {
			source.sendFailure(Msg.prefixed(Lang.get("ruin.admin.locate.none", id)));
			return 0;
		}
		long today = Encounters.day(source.getServer());
		source.sendSuccess(() -> Msg.prefixed(line(ruin, today)), false);
		return 1;
	}

	private static int map(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		return Safe.get("ruin.admin.map", () -> {
			CommandSourceStack source = ctx.getSource();
			RuinRecord ruin = find(source.getServer(), target.level(), target.blockPosition(), id, false);
			if (ruin == null) {
				source.sendFailure(Msg.prefixed(Lang.get("ruin.admin.locate.none", id)));
				return 0;
			}
			ItemStack item = RuinItems.forRuin(source.getServer(), ruin);
			if (item.isEmpty()) {
				source.sendFailure(Msg.prefixed(Lang.get("ruin.admin.locate.none", id)));
				return 0;
			}
			Component name = item.getHoverName();
			ItemUtil.giveOrDrop(target, item);
			source.sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.map", name.getString(), target.getName().getString())), true);
			return 1;
		}, 0);
	}

	/** Gives incantation scrolls to a player. */
	private static int incantation(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		return Safe.get("ruin.admin.incantation", () -> {
			IncantationItems.give(target, count);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.incantation", count, target.getName().getString())), true);
			return count;
		}, 0);
	}

	/**
	 * A ruin of this id near a position: the structure search in the level that generates it (Nether positions are
	 * divided by 8), else the nearest indexed ruin of that id.
	 */
	static @Nullable RuinRecord find(MinecraftServer server, ServerLevel from, BlockPos pos, String id, boolean claim) {
		Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> holders = new ArrayList<>();
		for (Identifier key : registry.keySet()) {
			if (id.equals(RuinRules.ruinIdOf(key.getPath()))) {
				registry.get(key).ifPresent(holders::add);
			}
		}
		if (!holders.isEmpty()) {
			HolderSet<Structure> set = HolderSet.direct(holders);
			Set<ServerLevel> levels = new LinkedHashSet<>();
			levels.add(from);
			levels.add(server.overworld());
			ServerLevel nether = server.getLevel(Level.NETHER);
			if (nether != null) {
				levels.add(nether);
			}
			for (ServerLevel level : levels) {
				boolean generates = false;
				for (Holder<Structure> holder : holders) {
					if (!level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).isEmpty()) {
						generates = true;
						break;
					}
				}
				if (!generates) {
					continue;
				}
				double scale = level.dimensionType().coordinateScale() / from.dimensionType().coordinateScale();
				BlockPos origin = level == from ? pos : BlockPos.containing(pos.getX() / scale, 64, pos.getZ() / scale);
				RuinRecord found = Cartography.locate(level, set, origin, RuinModule.config().cartography.searchRadius, claim);
				if (found != null) {
					return found;
				}
			}
		}
		RuinRecord indexed = RuinIndex.nearest(from.dimension(), pos.getX(), pos.getZ(), id);
		if (indexed == null) {
			for (RuinRecord ruin : RuinIndex.all()) {
				if (id.equals(ruin.ruinId)) {
					return ruin;
				}
			}
		}
		return indexed;
	}

	/** Preview of an ability by the guardian of the nearest running fight (cooldowns ignored). */
	private static int ability(CommandContext<CommandSourceStack> ctx, String style) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		RuinConfig.AbilityDef ability = new RuinConfig.AbilityDef();
		ability.type = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
		ability.style = style.toLowerCase(Locale.ROOT);
		if (!RuinConfig.ABILITIES.contains(ability.type) || !RuinConfig.STYLES.contains(ability.style)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.admin.ability.unknown", ability.type, String.join(", ", RuinConfig.ABILITIES))));
			return 0;
		}
		if (!Encounters.forceAbility(ruin, ability)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.admin.ability.failed", ability.type)));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.ability.used", ability.type)), false);
		return 1;
	}

	private static int finish(CommandContext<CommandSourceStack> ctx) {
		Guild guild = GuildArgument.resolve(ctx, "guild");
		if (guild == null) {
			return 0;
		}
		Research research = Cartography.research(guild);
		if (research == null || research.status != Research.Status.RUNNING) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.research.none")));
			return 0;
		}
		Cartography.addProgress(ctx.getSource().getServer(), guild, research.required);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.admin.research.finished", guild.name,
				Lang.raw(research.status == Research.Status.READY ? "ruin.admin.research.found" : "ruin.admin.research.nothing"))), true);
		return 1;
	}

	private static String pos(@Nullable BlockPos pos) {
		return pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static String dimensionName(RuinRecord ruin) {
		return Lang.raw(ruin.isNether() ? "ruin.dimension.nether" : "ruin.dimension.overworld");
	}
}
