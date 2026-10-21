package dev.guildy.scroll;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ralliement: after a long incantation, a war horn sounds and a pillar of amber and blue light rises where the caster stands
 * (the rally point). The guild members online within range (any dimension when {@code crossDimension} is on, never the ones
 * already there) receive a clickable [Rejoindre] in the chat and hear a distant horn: nobody moves without clicking it. A
 * click within {@code acceptSeconds} teleports the member next to the rally point (a free, safe spot), the first
 * {@code maxAllies} only ({@link RallyRules}); rings of light fall around each arrival. The call closes when the caster logs
 * out or leaves the dimension. Allowed during a horde (rally to defend) and to or from a ruin fight (walking in or out is
 * allowed too; the guardian keeps its own arena rules).
 */
final class RallySpell extends SpellEffect {
	private static final String COMMAND = "ralliement";
	private static final int ARRIVAL_TICKS = 30;
	private static final double PILLAR = 22;
	/** Calls kept after they expire (a late click gets "expired", not "unknown"). */
	private static final long FORGET_TICKS = 2 * 60 * 20L;
	private static final int MAX_INVITES = 128;
	private static final Map<Integer, Invite> INVITES = new LinkedHashMap<>();
	private static int nextId = 1;

	/** One call: who was called, who joined, until when. */
	static final class Invite {
		final int id;
		final UUID guild;
		final UUID caster;
		final String casterName;
		final ResourceKey<Level> dimension;
		final Vec3 point;
		final long expiresAt;
		final int max;
		final boolean crossDimension;
		final Set<UUID> invited = new HashSet<>();
		final Set<UUID> joined = new HashSet<>();
		boolean open = true;
		@Nullable RallySpell effect;

		Invite(int id, UUID guild, UUID caster, String casterName, ResourceKey<Level> dimension, Vec3 point, long expiresAt, int max,
				boolean crossDimension) {
			this.id = id;
			this.guild = guild;
			this.caster = caster;
			this.casterName = casterName;
			this.dimension = dimension;
			this.point = point;
			this.expiresAt = expiresAt;
			this.max = max;
			this.crossDimension = crossDimension;
		}
	}

	private record Arrival(Vec3 pos, int start) {
	}

	private final Invite invite;
	private final int duration;
	private final List<Arrival> arrivals = new ArrayList<>();

	private RallySpell(SpellContext ctx, Invite invite, int duration) {
		super(ctx);
		this.invite = invite;
		this.duration = duration;
	}

	// ================================================================== casting

	/** Lang key of the reason the rally cannot be sounded now (scroll kept), or null. */
	static @Nullable String check(SpellContext ctx) {
		if (ctx.guild == null) {
			return "scroll.fail.no_guild";
		}
		if (SwapRules.forCaster(SwapSpell.landing(ctx.level, ctx.caster, ctx.caster.position()).spot()) != SwapRules.Verdict.OK) {
			return "scroll.fail.rally_unsafe";
		}
		return called(ctx).isEmpty() ? "scroll.fail.no_members" : null;
	}

	/** The members who receive the call (see {@link RallyRules#called}). */
	static List<ServerPlayer> called(SpellContext ctx) {
		List<ServerPlayer> out = new ArrayList<>();
		Guild guild = ctx.guild;
		if (guild == null) {
			return out;
		}
		double range = ctx.p("range");
		boolean cross = ctx.i("crossDimension") > 0;
		Vec3 point = ctx.caster.position();
		for (UUID uuid : guild.memberIds()) {
			ServerPlayer member = GuildyServer.player(uuid);
			if (member == null) {
				continue;
			}
			boolean same = member.level() == ctx.level;
			double distance = same ? member.position().distanceTo(point) : Double.MAX_VALUE;
			if (RallyRules.called(member == ctx.caster, true, available(member), same, distance, range, cross)) {
				out.add(member);
			}
		}
		return out;
	}

	/** Sounds the rally. @return lang key of a failure (nothing happened), or null */
	static @Nullable String cast(SpellContext ctx) {
		String problem = check(ctx);
		if (problem != null) {
			return problem;
		}
		prune();
		List<ServerPlayer> members = called(ctx);
		int acceptTicks = ctx.ticks("acceptSeconds");
		Invite invite = new Invite(nextId++, ctx.guild.id, ctx.caster.getUUID(), ctx.caster.getName().getString(), ctx.level.dimension(),
				ctx.caster.position(), Scheduler.ticks() + acceptTicks, ctx.i("maxAllies"), ctx.i("crossDimension") > 0);
		for (ServerPlayer member : members) {
			invite.invited.add(member.getUUID());
		}
		INVITES.put(invite.id, invite);
		RallySpell effect = new RallySpell(ctx, invite, acceptTicks);
		invite.effect = effect;
		EffectRunner.start(effect);
		int seconds = RallyRules.secondsLeft(Scheduler.ticks(), invite.expiresAt);
		for (ServerPlayer member : members) {
			member.sendSystemMessage(message(invite, member, seconds));
			Msg.sound(member, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2), 1.0F, 1.0F);
		}
		Msg.actionBar(ctx.caster, "scroll.rally.called", members.size());
		return null;
	}

	/** The call in the chat: who, where, and the clickable [Rejoindre]. */
	private static Component message(Invite invite, ServerPlayer member, int seconds) {
		String where = member.level().dimension() == invite.dimension
				? Lang.raw("scroll.rally.where.distance", (int) Math.round(member.position().distanceTo(invite.point)))
				: Lang.raw("scroll.rally.where.dimension");
		MutableComponent hover = Lang.get("scroll.rally.join.hover", invite.casterName, seconds, invite.max);
		MutableComponent button = Lang.get("scroll.rally.join").withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand("/guild " + COMMAND + " " + invite.id))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
		return Msg.prefixed(Lang.get("scroll.rally.invite", invite.casterName, where).append(button));
	}

	/** {@code /guild ralliement <id>}: the [Rejoindre] of a call. */
	static void registerCommand() {
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal(COMMAND)
				.then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(cmd -> {
					ServerPlayer player = GuildCommand.player(cmd);
					int id = IntegerArgumentType.getInteger(cmd, "id");
					return Safe.get("scroll.rally.join", () -> join(player, id), 0);
				}))));
	}

	// ================================================================== joining

	/** A click on [Rejoindre]. @return 1 when the member was teleported */
	static int join(ServerPlayer player, int id) {
		prune();
		Invite invite = INVITES.get(id);
		Guild guild = GuildManager.of(player);
		RallyRules.Verdict verdict;
		ServerLevel level = invite == null ? null : player.level().getServer().getLevel(invite.dimension);
		if (invite == null || level == null) {
			verdict = RallyRules.Verdict.NOT_INVITED;
		} else {
			verdict = RallyRules.verdict(new RallyRules.Click(Scheduler.ticks(), invite.expiresAt, invite.open, invite.invited.contains(player.getUUID()),
					guild != null && guild.id.equals(invite.guild), invite.joined.contains(player.getUUID()), invite.joined.size(), invite.max,
					player.level().dimension() == invite.dimension, invite.crossDimension, available(player)));
		}
		if (verdict != RallyRules.Verdict.OK) {
			Msg.send(player, RallyRules.failureKey(verdict));
			Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
			return 0;
		}
		Vec3 spot = arrivalSpot(level, player, invite.point, invite.joined.size());
		if (spot == null) {
			Msg.send(player, "scroll.rally.fail.unsafe");
			Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
			return 0;
		}
		invite.joined.add(player.getUUID());
		departure(player);
		if (player.isPassenger()) {
			player.stopRiding();
		}
		Vec3 look = invite.point.subtract(spot);
		float yaw = look.horizontalDistanceSqr() < 1.0E-4 ? player.getYRot() : (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, true);
		player.resetFallDistance();
		Msg.actionBar(player, "scroll.rally.arrived", invite.casterName);
		RallySpell effect = invite.effect;
		if (effect != null && !effect.done()) {
			effect.arrivals.add(new Arrival(spot, effect.age));
		} else {
			Fx fx = new Fx(level, spot, 200, ScrollModule.config().limits.viewDistance);
			fx.burst(ParticleTypes.TOTEM_OF_UNDYING, spot.x, spot.y + 1, spot.z, 20, 0.3, 0.6, 0.3, 0.25);
			fx.flush();
		}
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.PLAYER_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.1F);
		ServerPlayer caster = GuildyServer.player(invite.caster);
		if (caster != null) {
			Msg.actionBar(caster, "scroll.rally.joined", player.getName().getString(), invite.joined.size(), invite.max);
			Msg.sound(caster, SoundEvents.NOTE_BLOCK_BELL, 0.8F, 1.4F);
		}
		return 1;
	}

	/** Alive, awake, not spectating. */
	private static boolean available(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isRemoved();
	}

	/** A free, safe spot around the rally point for the n-th arrival (the point itself last), or null. */
	private static @Nullable Vec3 arrivalSpot(ServerLevel level, ServerPlayer player, Vec3 point, int n) {
		int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-2, -1},
				{1, 2}, {-1, -2}};
		for (int k = 0; k < offsets.length; k++) {
			int[] o = offsets[(n + k) % offsets.length];
			for (int dy : new int[] {0, 1, -1}) {
				Vec3 feet = new Vec3(Math.floor(point.x + o[0]) + 0.5, point.y + dy, Math.floor(point.z + o[1]) + 0.5);
				SwapSpell.Landing landing = SwapSpell.landing(level, player, feet);
				if (SwapRules.forCaster(landing.spot()) == SwapRules.Verdict.OK && Math.abs(landing.feet().y - point.y) <= 2) {
					return landing.feet();
				}
			}
		}
		SwapSpell.Landing center = SwapSpell.landing(level, player, point);
		return SwapRules.forCaster(center.spot()) == SwapRules.Verdict.OK ? center.feet() : null;
	}

	/** The column of light where the member stood before leaving (their own level). */
	private static void departure(ServerPlayer player) {
		Vec3 p = player.position();
		Spell spell = Spell.RALLY;
		Fx fx = new Fx(player.level(), p, 220, ScrollModule.config().limits.viewDistance);
		fx.dot(Fx.flash(spell.color), p.x, p.y + 1, p.z);
		for (int s = 0; s < 2; s++) {
			for (int k = 0; k < 16; k++) {
				double angle = s * Math.PI + k * 0.6;
				fx.move(Fx.fade(spell.color, spell.accent, 1.1F), p.x + Math.cos(angle) * 0.7, p.y + k * 0.15, p.z + Math.sin(angle) * 0.7, 0, 0.25, 0);
			}
		}
		fx.burst(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 30, 0.3, 0.6, 0.3, 0.2);
		fx.flush();
		player.level().playSound(null, p.x, p.y, p.z, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	/** Calls long over are forgotten (bounded map). */
	private static void prune() {
		long now = Scheduler.ticks();
		Iterator<Invite> it = INVITES.values().iterator();
		while (it.hasNext()) {
			Invite invite = it.next();
			if (invite.expiresAt + FORGET_TICKS < now || INVITES.size() > MAX_INVITES) {
				it.remove();
			}
		}
	}

	/** The command of the newest open call addressed to this member that they have not joined yet, or null (tests). */
	static @Nullable String pendingCommand(UUID member) {
		Invite found = null;
		for (Invite invite : INVITES.values()) {
			if (invite.open && invite.invited.contains(member) && !invite.joined.contains(member) && invite.expiresAt >= Scheduler.ticks()) {
				found = invite;
			}
		}
		return found == null ? null : "guild " + COMMAND + " " + found.id;
	}

	/** Server stop: every call ends. */
	static void clearAll() {
		INVITES.clear();
	}

	// ================================================================== animation

	@Override
	Vec3 anchor() {
		return invite.point;
	}

	@Override
	void end(boolean cancelled) {
		if (cancelled) {
			// The caster logged out or left the dimension: the call is over.
			invite.open = false;
		}
		invite.effect = null;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		Vec3 p = invite.point;
		if (age == 0) {
			sound(p, SoundEvents.RAID_HORN, 4.0F, 1.0F);
			sound(p, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2), 4.0F, 1.0F);
			sound(p, SoundEvents.BEACON_ACTIVATE, 2.0F, 1.2F);
			fx.dot(Fx.flash(spell.color), p.x, p.y + 1.5, p.z);
			fx.sphereBurst(Fx.fade(spell.color, spell.accent, 1.4F), p.add(0, 1.2, 0), 0.5, 48, 0.3);
		}
		double phase = age * 0.08;
		// The pillar of light: a beam of alternating colors and two rising spirals.
		if (age % 2 == 0) {
			int points = (int) (PILLAR * 2);
			for (int i = 0; i < points; i++) {
				double y = i * 0.5 + (age % 4) * 0.25;
				if (!fx.dot(Fx.dust((i + age / 2) % 4 < 2 ? spell.color : spell.accent, 1.2F), p.x, p.y + y, p.z)) {
					break;
				}
			}
		}
		for (int s = 0; s < 2; s++) {
			double angle = phase * 3 + s * Math.PI;
			double y = (age * 0.35) % PILLAR;
			fx.move(ParticleTypes.END_ROD, p.x + Math.cos(angle) * 0.9, p.y + y, p.z + Math.sin(angle) * 0.9, 0, 0.1, 0);
		}
		if (age % 4 == 0) {
			fx.runeCircle(p.add(0, 0.1, 0), 2.6, spell.color, spell.accent, 1.0, phase);
		}
		// A beacon pulse every second: a ring sweeping out and a light at the top.
		int beat = age % 20;
		if (beat < 10) {
			double r = 0.8 + 6 * easeOut(beat / 10.0);
			fx.ring(Fx.fade(spell.color, spell.accent, 1.2F), p.add(0, 0.2, 0), r, (int) Math.max(12, r * 5), phase);
		}
		if (beat == 0) {
			fx.dot(Fx.flash(spell.accent), p.x, p.y + PILLAR, p.z);
			ServerPlayer caster = caster();
			if (caster != null && caster.level() == level) {
				Msg.actionBar(caster, "scroll.rally.status", invite.joined.size(), invite.max, RallyRules.secondsLeft(Scheduler.ticks(), invite.expiresAt));
			}
		}
		drawArrivals(fx, spell);
		boolean full = invite.joined.size() >= invite.max;
		boolean settled = arrivals.isEmpty() || age - arrivals.getLast().start() > ARRIVAL_TICKS;
		if (age >= duration || full && settled) {
			fx.sphereBurst(Fx.dust(spell.accent, 1.2F), p.add(0, 1, 0), 1.5, 40, 0.12);
			sound(p, SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.1F);
			finish();
		}
	}

	/** Rings of light falling around each arrival and closing in. */
	private void drawArrivals(Fx fx, Spell spell) {
		for (Arrival arrival : arrivals) {
			int t = age - arrival.start();
			if (t < 0 || t > ARRIVAL_TICKS) {
				continue;
			}
			Vec3 a = arrival.pos();
			double k = t / (double) ARRIVAL_TICKS;
			if (t == 0) {
				fx.dot(Fx.flash(spell.color), a.x, a.y + 1, a.z);
				fx.burst(ParticleTypes.TOTEM_OF_UNDYING, a.x, a.y + 1, a.z, 20, 0.3, 0.6, 0.3, 0.25);
			}
			fx.ring(Fx.fade(spell.accent, spell.color, 1.1F), a.add(0, 3.0 * (1 - k), 0), 1.2 * (1 - k) + 0.4, 16, age * 0.5);
			fx.burst(ParticleTypes.END_ROD, a.x, a.y + 0.2, a.z, 2, 0.4, 0.1, 0.4, 0.02);
		}
	}
}
