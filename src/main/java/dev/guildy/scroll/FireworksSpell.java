package dev.guildy.scroll;

import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.BannerDesign;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grand spectacle: the caster faces their audience and a line of rune circles is traced on the ground behind them (drawn from
 * the middle outward during the incantation), then a real fireworks show is played from those circles following a
 * {@link ShowPlan}: vanilla firework rockets with their explosions (shapes, colours, fades, trails, twinkles), launched so
 * that they burst exactly on the beats of the plan, shapes drawn with particles around some bursts (hearts, stars, a rune
 * circle in the sky, the crown of the final burst), the circles glowing in the colours of the act and flaring at each launch,
 * a horn to open, another for the finale, a fanfare for the last burst.
 *
 * <p>Harmless: the rockets are tagged {@value #TAG}, fly without collisions (they cross players and leaves without hitting
 * them) and burst on the plan's tick through the vanilla firework entity event, without their explosion damage; any damage a
 * tagged rocket would still deal (a show stopped mid-flight, a reloaded chunk) is cancelled, and a tagged rocket saved to disk
 * never comes back. No block is ever placed or broken. Launch points are on open ground under the open sky only (not on
 * water or lava, not in another guild's territory). The show goes on when the caster leaves (it belongs to the audience);
 * nothing is launched while nobody is close enough to see it; at most {@code limits.maxFireworks} rockets fly on the server.
 */
final class FireworksSpell extends SpellEffect {
	/** Tag of the show's rockets. */
	static final String TAG = "guildy_firework";
	/** Launch points needed under the open sky. */
	static final int MIN_POINTS = 3;
	/** No new show this close (blocks) to a running one. */
	static final double NEARBY = 32;
	static final double MIN_WIDTH = 8;
	static final double MAX_WIDTH = 32;
	static final double MIN_HEIGHT = 14;
	static final double MAX_HEIGHT = 44;
	/** Ground of a launch point: at most this far above / below the caster's feet. */
	private static final int MAX_RISE = 6;
	private static final int MAX_DROP = 10;
	/** Rockets in flight at most for one show (the cursed show is bigger). */
	private static final int ALIVE = 40;
	private static final int CURSED_ALIVE = 48;
	/** The show's rockets in flight on the server. */
	private static final Set<UUID> LIVE = new HashSet<>();

	/** Launch points found on the terrain (left to right as the audience sees them), and the points refused. */
	record Stage(ShowStage.Layout layout, List<Vec3> spots, List<Vec3> refused, double y) {
		Vec3 center() {
			return new Vec3(layout.centerX(), y, layout.centerZ());
		}
	}

	private record Flying(FireworkRocketEntity rocket, ShowPlan.Rocket plan) {
	}

	/** A figure being drawn around a burst. */
	private record Bloom(ShowPlan.Rocket rocket, Vec3 at, int start) {
	}

	private final List<Vec3> spots;
	private final Vec3 center;
	private final Vec3 right;
	private final ShowPlan plan;
	private final double circle;
	private final int[] ignition;
	private final int[] flare;
	private final List<Flying> flying = new ArrayList<>();
	private final List<Bloom> blooms = new ArrayList<>();
	private int next;
	private int launched;
	private int exploded;
	private int skipped;
	private boolean watched = true;
	private ShowPlan.@Nullable Span shown;
	private int[] from = new int[2];
	private int changed;

	FireworksSpell(SpellContext ctx, Stage stage) {
		super(ctx);
		this.spots = List.copyOf(stage.spots());
		this.center = stage.center();
		this.right = new Vec3(stage.layout().rightX(), 0, stage.layout().rightZ());
		this.circle = circleRadius(stage.layout().spacing());
		int n = spots.size();
		int ticks = Mth.clamp(ctx.ticks("duration"), ShowPlan.MIN_TICKS, ShowPlan.MAX_TICKS);
		double height = Mth.clamp(ctx.p("height"), MIN_HEIGHT, MAX_HEIGHT);
		long seed = ctx.level.getGameTime() * 31L + ctx.caster.getUUID().getLeastSignificantBits();
		this.plan = ShowPlan.build(new ShowPlan.Settings(seed, ticks, n, stage.layout().spacing() * Math.max(0, n - 1), height, ctx.cursed(),
				guildColors(ctx.guild), ctx.cursed() ? CURSED_ALIVE : ALIVE));
		this.ignition = new int[n];
		this.flare = new int[n];
		int rings = Math.max(1, ShowStage.fromMiddle(0, n));
		for (int i = 0; i < n; i++) {
			ignition[i] = ShowStage.fromMiddle(i, n) * (ShowPlan.INTRO - 4) / rings;
		}
		ShowPlan.Palette first = plan.acts().getFirst().palette();
		this.from = new int[] {first.color(0), first.color(1)};
	}

	// ================================================================== registration, safety

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> Safe.get("scroll.fireworks.damage", () -> allowDamage(source), true));
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) ->
				Safe.get("scroll.fireworks.load", () -> allowLoad(entity, fromDisk), true));
		Lifecycle.STOPPING.add(server -> LIVE.clear());
	}

	/** ServerLivingEntityEvents.ALLOW_DAMAGE: a show's rocket never hurts anyone. */
	static boolean allowDamage(DamageSource source) {
		return !isShowRocket(source.getDirectEntity()) && !isShowRocket(source.getEntity());
	}

	/**
	 * ServerEntityEvents.ALLOW_LOAD: a show's rocket saved to disk (its chunk unloaded mid-flight, a server stop) never comes
	 * back; the show it belonged to has forgotten it.
	 */
	static boolean allowLoad(Entity entity, boolean fromDisk) {
		return !fromDisk || !isShowRocket(entity);
	}

	static boolean isShowRocket(@Nullable Entity entity) {
		return entity instanceof FireworkRocketEntity && entity.entityTags().contains(TAG);
	}

	// ================================================================== the stage

	/** Why the show cannot start now (lang key, see {@link SpellFailure}), or null. */
	static @Nullable String check(SpellContext ctx) {
		List<FireworksSpell> shows = EffectRunner.all(FireworksSpell.class);
		for (FireworksSpell show : shows) {
			if (show.owner.equals(ctx.caster.getUUID())) {
				return "scroll.fail.show_running";
			}
		}
		Stage stage = stage(ctx);
		for (FireworksSpell show : shows) {
			if (show.level == ctx.level && show.center.distanceTo(stage.center()) < NEARBY) {
				return "scroll.fail.show_nearby";
			}
		}
		if (stage.spots().size() < MIN_POINTS) {
			return SpellFailure.of("scroll.fail.show_no_sky", stage.spots().size(), stage.layout().spots().size(), MIN_POINTS);
		}
		return null;
	}

	/** The stage of a cast: the layout behind the caster, each point on the ground under the open sky (or refused). */
	static Stage stage(SpellContext ctx) {
		ServerPlayer caster = ctx.caster;
		double yaw = Math.toRadians(caster.getYRot());
		double width = Mth.clamp(ctx.p("width"), MIN_WIDTH, MAX_WIDTH);
		int points = Mth.clamp(ctx.i("points"), MIN_POINTS, ShowPlan.MAX_POINTS);
		ShowStage.Layout layout = ShowStage.layout(caster.getX(), caster.getZ(), -Math.sin(yaw), Math.cos(yaw), width, points);
		List<Vec3> spots = new ArrayList<>();
		List<Vec3> refused = new ArrayList<>();
		double y = 0;
		for (ShowStage.Spot spot : layout.spots()) {
			Vec3 ground = ground(ctx.level, spot.x(), spot.z(), caster.getY(), ctx.guild);
			if (ground == null) {
				refused.add(new Vec3(spot.x(), caster.getY(), spot.z()));
			} else {
				spots.add(ground);
				y += ground.y;
			}
		}
		return new Stage(layout, spots, refused, spots.isEmpty() ? caster.getY() : y / spots.size());
	}

	/**
	 * The launch point of (x, z): the ground of the column at the caster's level (the first solid block under the caster's
	 * feet height when it is free, at most {@link #MAX_DROP} blocks down; else the first free block above solid ground, at
	 * most {@link #MAX_RISE} blocks up), under the open sky (nothing that blocks motion above: no roof, ceiling, leaves).
	 * Null on water or lava, in an unloaded chunk or in the territory of another guild.
	 */
	private static @Nullable Vec3 ground(ServerLevel level, double x, double z, double feet, @Nullable Guild guild) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int base = Mth.floor(feet + 0.01);
		if (!level.isLoaded(new BlockPos(bx, base, bz))) {
			return null;
		}
		Integer found = null;
		if (free(level, bx, base, bz)) {
			for (int y = base; y > base - MAX_DROP && y > level.getMinY(); y--) {
				BlockPos below = new BlockPos(bx, y - 1, bz);
				if (!level.getFluidState(below).isEmpty()) {
					return null;
				}
				if (!free(level, bx, y - 1, bz)) {
					found = y;
					break;
				}
			}
		} else {
			for (int y = base + 1; y <= base + MAX_RISE; y++) {
				if (free(level, bx, y, bz)) {
					if (!level.getFluidState(new BlockPos(bx, y - 1, bz)).isEmpty()) {
						return null;
					}
					found = y;
					break;
				}
			}
		}
		// Open sky: the first free block above every block that blocks motion (or holds a fluid) of the column.
		if (found == null || level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) > found) {
			return null;
		}
		Guild owner = GuildManager.at(level, new BlockPos(bx, found - 1, bz));
		if (owner != null && owner != guild) {
			return null;
		}
		return new Vec3(x, found, z);
	}

	/** No collision and no fluid at this block. */
	private static boolean free(ServerLevel level, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Radius of the rune circles for this gap between the points. */
	static double circleRadius(double spacing) {
		return Mth.clamp(spacing * 0.42, 0.6, 1.15);
	}

	/** The guild's banner colours as firework colours (base first), at most four. */
	static List<Integer> guildColors(@Nullable Guild guild) {
		if (guild == null || guild.banner == null) {
			return List.of();
		}
		Set<Integer> colors = new LinkedHashSet<>();
		colors.add(guild.banner.baseColor().getFireworkColor());
		if (guild.banner.layers != null) {
			for (BannerDesign.Layer layer : guild.banner.layers) {
				DyeColor dye = layer == null || layer.color == null ? null : DyeColor.byName(layer.color, null);
				if (dye != null && colors.size() < 4) {
					colors.add(dye.getFireworkColor());
				}
			}
		}
		return List.copyOf(colors);
	}

	/**
	 * During the incantation: the circles of the stage the caster would get now, drawn from the middle outward at the pace of
	 * the channel (complete before the spell fires); a puff of smoke where a point is refused.
	 */
	static void preview(SpellContext ctx, double progress, long elapsed) {
		Stage stage = stage(ctx);
		Fx fx = new Fx(ctx.level, stage.center(), 480, ScrollModule.config().limits.viewDistance);
		List<Vec3> spots = stage.spots();
		int n = spots.size();
		int rings = Math.max(1, ShowStage.fromMiddle(0, n) + 1);
		double radius = circleRadius(stage.layout().spacing());
		Spell spell = ctx.spell;
		for (int i = 0; i < n; i++) {
			double start = 0.7 * ShowStage.fromMiddle(i, n) / rings;
			double local = Mth.clamp((progress - start) / 0.3, 0, 1);
			if (local <= 0 || local >= 1 && (elapsed + i) % 3 != 0) {
				continue;
			}
			Vec3 spot = spots.get(i);
			int main = ctx.cursed() ? Fx.mix(spell.color, 0x8E2BFF, 0.6) : spell.color;
			fx.runeCircle(spot.add(0, 0.06, 0), radius, main, spell.accent, local, elapsed * 0.08 + i * 0.7);
			if (local < 1 && elapsed % 2 == 0) {
				fx.move(ParticleTypes.END_ROD, spot.x, spot.y + 0.1, spot.z, 0, 0.05, 0);
			}
		}
		if (elapsed % 10 == 0) {
			for (Vec3 refused : stage.refused()) {
				fx.burst(ParticleTypes.SMOKE, refused.x, refused.y + 0.3, refused.z, 3, 0.2, 0.1, 0.2, 0.0);
			}
		}
		fx.flush();
	}

	// ================================================================== the show

	@Override
	Vec3 anchor() {
		return center;
	}

	/** The show belongs to its audience: it goes on when the caster leaves. */
	@Override
	boolean bound() {
		return false;
	}

	@Override
	void tick(Fx fx) {
		if (age == 0) {
			open();
		}
		if (age % 20 == 0) {
			watched = watched();
		}
		follow(plan.spanAt(age));
		if (watched) {
			circles(fx);
		}
		launch(fx);
		burst();
		if (watched) {
			blooms(fx);
		}
		cues(fx);
		if (age >= plan.ticks()) {
			finish();
		}
	}

	@Override
	void end(boolean cancelled) {
		stop();
	}

	/** Removes the rockets still in flight. */
	private void stop() {
		for (Flying f : flying) {
			if (!f.rocket().isRemoved()) {
				f.rocket().discard();
			}
			LIVE.remove(f.rocket().getUUID());
		}
		flying.clear();
	}

	private boolean watched() {
		double max = ScrollModule.config().limits.viewDistance;
		for (ServerPlayer player : level.players()) {
			if (!player.isRemoved() && player.distanceToSqr(center) <= max * max) {
				return true;
			}
		}
		return false;
	}

	private void announce(String key, Object... args) {
		double max = ScrollModule.config().limits.viewDistance;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= max * max) {
				Msg.actionBar(player, key, args);
			}
		}
	}

	private void open() {
		if (ctx.cursed()) {
			sound(center, SoundEvents.RAID_HORN, 4.0F, 0.7F);
		} else {
			sound(center, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1), 4.0F, 1.0F);
		}
		announce(ctx.cursed() ? "scroll.show.start_cursed" : "scroll.show.start", ctx.caster.getName().getString());
	}

	/** Horn and message of the finale, fanfare of the final burst. */
	private void cues(Fx fx) {
		if (age == plan.finale().start()) {
			sound(center, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(ctx.cursed() ? 6 : 2), 4.0F, ctx.cursed() ? 0.8F : 1.1F);
			announce(ctx.cursed() ? "scroll.show.finale_cursed" : "scroll.show.finale");
		}
		if (age == plan.finalBurst()) {
			if (ctx.cursed()) {
				sound(center, SoundEvents.WITHER_AMBIENT, 3.0F, 0.6F);
				sound(center, SoundEvents.SOUL_ESCAPE, 4.0F, 0.6F);
			} else {
				sound(center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 3.0F, 1.0F);
			}
			for (int i = 0; i < spots.size(); i++) {
				flare[i] = 12;
				if (watched) {
					fx.arc(Fx.dust(from[1], 1.4F), spots.get(i).add(0, 0.1, 0), circle * 1.2, 0, Math.PI * 2, 14, true);
				}
			}
		}
	}

	// ------------------------------------------------------------------ circles

	/** Colours of the act on the circles: when the act changes, they shift in one second. */
	private void follow(ShowPlan.Span span) {
		if (span != shown) {
			if (shown != null) {
				from = colors();
				changed = age;
			}
			shown = span;
		}
	}

	private int[] colors() {
		ShowPlan.Palette palette = shown == null ? plan.acts().getFirst().palette() : shown.palette();
		double k = Math.min(1, (age - changed) / 20.0);
		return new int[] {Fx.mix(from[0], palette.color(0), k), Fx.mix(from[1], palette.color(1), k)};
	}

	/**
	 * The rune circles: lit one after the other from the middle during the ignition, redrawn every 4 ticks in the colours of
	 * the act (each turning its own way), flaring at a launch, undrawn during the last two seconds.
	 */
	private void circles(Fx fx) {
		int fading = plan.ticks() - ShowPlan.OUTRO;
		double progress = age < fading ? 1 : Math.max(0, 1 - (age - fading) / (double) ShowPlan.OUTRO);
		int[] colors = colors();
		for (int i = 0; i < spots.size(); i++) {
			Vec3 spot = spots.get(i);
			if (age == ignition[i]) {
				ignite(fx, i, colors[1]);
			}
			boolean lit = age >= ignition[i];
			boolean flaring = flare[i] > 0;
			if (flaring) {
				flare[i]--;
			}
			if (flaring ? flare[i] % 2 != 0 : (age + i) % 4 != 0) {
				continue;
			}
			int main = lit ? colors[0] : Fx.mix(colors[0], 0x101010, 0.65);
			int accent = lit ? colors[1] : Fx.mix(colors[1], 0x101010, 0.65);
			if (flaring) {
				main = Fx.mix(main, 0xFFFFFF, 0.45);
				accent = Fx.mix(accent, 0xFFFFFF, 0.6);
			}
			double phase = (i % 2 == 0 ? 1 : -1) * age * 0.05 + i * 0.7;
			fx.runeCircle(spot.add(0, 0.06, 0), circle, main, accent, progress, phase);
			if (lit && progress >= 1 && (age + i) % 12 == 0) {
				fx.move(ParticleTypes.END_ROD, spot.x, spot.y + 0.15, spot.z, 0, 0.05, 0);
			}
		}
	}

	/** A circle lights up: sparks, a light rising, a chime (higher toward the ends). */
	private void ignite(Fx fx, int i, int color) {
		Vec3 spot = spots.get(i);
		fx.burst(Fx.dust(color, 1.2F), spot.x, spot.y + 0.2, spot.z, 8, circle * 0.5, 0.05, circle * 0.5, 0.0);
		fx.move(ParticleTypes.END_ROD, spot.x, spot.y + 0.1, spot.z, 0, 0.12, 0);
		flare[i] = 4;
		float pitch = 0.6F + 0.15F * ShowStage.fromMiddle(i, spots.size());
		sound(spot, ctx.cursed() ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME, 1.6F, Math.min(2.0F, pitch));
	}

	// ------------------------------------------------------------------ rockets

	private void launch(Fx fx) {
		List<ShowPlan.Rocket> rockets = plan.rockets();
		int limit = ScrollModule.config().limits.maxFireworks;
		while (next < rockets.size() && rockets.get(next).launch() <= age) {
			ShowPlan.Rocket rocket = rockets.get(next++);
			if (!watched || LIVE.size() >= limit) {
				skipped++;
				continue;
			}
			FireworkRocketEntity entity = spawn(rocket);
			if (entity == null) {
				skipped++;
				continue;
			}
			flying.add(new Flying(entity, rocket));
			launched++;
			flare[rocket.point()] = 6;
			Vec3 spot = spots.get(rocket.point());
			int color = rocket.explosions().getFirst().colors().getFirst();
			fx.burst(ParticleTypes.FIREWORK, spot.x, spot.y + 0.2, spot.z, 3, 0.15, 0.05, 0.15, 0.08);
			fx.arc(Fx.dust(color, 1.1F), spot.add(0, 0.1, 0), circle * 0.55, 0, Math.PI * 2, 10, true);
		}
	}

	/**
	 * A vanilla firework rocket with the explosions of the plan, starting at the speed that brings it to its burst point (it
	 * is told to burst by {@link #burst}, its vanilla lifetime always longer), flying through anything, tagged.
	 */
	private @Nullable FireworkRocketEntity spawn(ShowPlan.Rocket rocket) {
		Vec3 from = spots.get(rocket.point()).add(0, 0.15, 0);
		if (!level.isLoaded(BlockPos.containing(from))) {
			return null;
		}
		List<FireworkExplosion> explosions = new ArrayList<>();
		for (ShowPlan.Explosion explosion : rocket.explosions()) {
			explosions.add(vanilla(explosion));
		}
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(Math.min(255, rocket.flight() / 10 + 2), explosions));
		FireworkRocketEntity entity = new FireworkRocketEntity(level, from.x, from.y, from.z, stack);
		double side = ShowPlan.sideSpeed(1.0, rocket.flight());
		Vec3 forward = new Vec3(-right.z, 0, right.x);
		Vec3 horizontal = right.scale(rocket.drift() * side).add(forward.scale(rocket.depth() * side));
		entity.setDeltaMovement(horizontal.x, ShowPlan.launchSpeed(rocket.height(), rocket.flight()), horizontal.z);
		entity.noPhysics = true;
		entity.addTag(TAG);
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		LIVE.add(entity.getUUID());
		return entity;
	}

	private static FireworkExplosion vanilla(ShowPlan.Explosion explosion) {
		FireworkExplosion.Shape shape = switch (explosion.shape()) {
			case SMALL_BALL -> FireworkExplosion.Shape.SMALL_BALL;
			case LARGE_BALL -> FireworkExplosion.Shape.LARGE_BALL;
			case STAR -> FireworkExplosion.Shape.STAR;
			case CREEPER -> FireworkExplosion.Shape.CREEPER;
			case BURST -> FireworkExplosion.Shape.BURST;
		};
		IntArrayList colors = new IntArrayList();
		for (int color : explosion.colors()) {
			colors.add(color);
		}
		IntArrayList fades = new IntArrayList();
		for (int fade : explosion.fades()) {
			fades.add(fade);
		}
		return new FireworkExplosion(shape, colors, fades, explosion.trail(), explosion.twinkle());
	}

	/** The rockets due burst now (the vanilla explosion of the entity event, never its damage), figures start. */
	private void burst() {
		Iterator<Flying> it = flying.iterator();
		while (it.hasNext()) {
			Flying f = it.next();
			FireworkRocketEntity rocket = f.rocket();
			if (rocket.isRemoved()) {
				LIVE.remove(rocket.getUUID());
				it.remove();
				continue;
			}
			if (f.plan().burst() > age) {
				continue;
			}
			Vec3 at = rocket.position();
			level.broadcastEntityEvent(rocket, EntityEvent.FIREWORKS_EXPLODE);
			rocket.discard();
			LIVE.remove(rocket.getUUID());
			it.remove();
			exploded++;
			if (f.plan().figure() != ShowPlan.Figure.NONE) {
				blooms.add(new Bloom(f.plan(), at, age));
			}
		}
	}

	// ------------------------------------------------------------------ figures

	private void blooms(Fx fx) {
		blooms.removeIf(bloom -> age - bloom.start() > 40);
		for (Bloom bloom : blooms) {
			bloom(fx, bloom, age - bloom.start());
		}
	}

	/**
	 * A figure facing the audience around a burst: lights fly from the burst to the outline, which glows (fading to the
	 * explosion's fade colour) and droops as it dies out; a willow rains golden (cursed: purple) drops.
	 */
	private void bloom(Fx fx, Bloom bloom, int t) {
		ShowPlan.Explosion first = bloom.rocket().explosions().getFirst();
		int color = first.colors().getFirst();
		int second = first.colors().size() > 1 ? first.colors().get(1) : color;
		int fade = first.fades().isEmpty() ? Fx.mix(color, 0x000000, 0.55) : first.fades().getFirst();
		double scale = Math.max(3.0, bloom.rocket().height() * 0.19) * (ctx.cursed() ? 1.1 : 1.0);
		if (bloom.rocket().figure() == ShowPlan.Figure.WILLOW) {
			willow(fx, bloom.at(), t, color, fade, scale);
			return;
		}
		List<double[]> outline = ShowFigures.outline(bloom.rocket().figure());
		switch (t) {
			case 0 -> {
				for (int i = 0; i < outline.size(); i++) {
					fx.dot(Fx.trail(point(bloom.at(), outline.get(i), scale, 0), i % 2 == 0 ? color : second, 8), bloom.at());
				}
			}
			case 8 -> {
				for (int i = 0; i < outline.size(); i++) {
					Vec3 p = point(bloom.at(), outline.get(i), scale, 0);
					fx.dot(Fx.fade(i % 3 == 0 ? second : color, fade, 2.4F), p);
					if (i % 4 == 0) {
						fx.move(ParticleTypes.FIREWORK, p, new Vec3(0, -0.03, 0));
					}
				}
			}
			case 15 -> {
				for (int i = 0; i < outline.size(); i += 2) {
					fx.dot(Fx.fade(color, fade, 2.0F), point(bloom.at(), outline.get(i), scale, 0.12));
				}
			}
			case 23 -> {
				for (int i = 0; i < outline.size(); i += 3) {
					fx.dot(Fx.fade(fade, Fx.mix(fade, 0x000000, 0.7), 1.6F), point(bloom.at(), outline.get(i), scale, 0.3));
				}
			}
			default -> {
			}
		}
	}

	/** A point of an outline around the burst, in the plane facing the audience, sagging by {@code droop} of the size. */
	private Vec3 point(Vec3 at, double[] p, double scale, double droop) {
		return at.add(right.scale(p[0] * scale)).add(0, (p[1] - droop) * scale, 0);
	}

	private void willow(Fx fx, Vec3 at, int t, int color, int fade, double scale) {
		if (t < 3 || t > 36 || t % 3 != 0) {
			return;
		}
		double radius = scale * (0.35 + t / 50.0);
		for (int i = 0; i < 9; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double down = -0.2 - random.nextDouble() * 0.8;
			double flat = Math.sqrt(1 - down * down);
			Vec3 p = at.add(Math.cos(angle) * flat * radius, down * radius - t * 0.05, Math.sin(angle) * flat * radius);
			fx.dot(ctx.cursed() ? ParticleTypes.FALLING_OBSIDIAN_TEAR : ParticleTypes.FALLING_NECTAR, p);
			if (i % 3 == 0) {
				fx.dot(Fx.fade(color, fade, 1.4F), p);
			}
		}
	}

	// ================================================================== queries (tests, admin)

	/** The running show of this caster, or null. */
	static @Nullable FireworksSpell of(UUID owner) {
		for (FireworksSpell show : EffectRunner.all(FireworksSpell.class)) {
			if (show.owner.equals(owner)) {
				return show;
			}
		}
		return null;
	}

	ScrollModule.ShowStatus status() {
		return new ScrollModule.ShowStatus(spots.size(), plan.ticks(), age, plan.rockets().size(), launched, exploded, flying.size(), skipped,
				ctx.cursed(), List.copyOf(spots));
	}

	/** Ends the show at once (its rockets removed). */
	void stopNow() {
		stop();
		finish();
	}

	/** The show's rockets in flight on the server. */
	static int inFlight() {
		return LIVE.size();
	}
}
