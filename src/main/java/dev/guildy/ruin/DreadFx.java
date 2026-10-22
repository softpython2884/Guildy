package dev.guildy.ruin;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Colours and drawing helpers of the dread attacks, on top of the budgeted {@link RitualFx}: the theme of each style (crypt
 * bone, arcane, sand, ice, jade, poison, sun, lava, soul, wither, ghast fire, Nether rift), the warnings drawn on the floor
 * before a strike (red lines, cones, rings with their safe gaps, the green circle of a safe spot) and the eruptions.
 */
final class DreadFx {
	static final int WARNING = CursedFx.WARNING;
	static final int SAFE = 0x5CFF7A;

	/** The look of a style: main and accent colours, a signature particle and a block for the debris. */
	record Theme(int primary, int secondary, ParticleOptions particle, ParticleOptions accent, ParticleOptions debris) {
	}

	private DreadFx() {
	}

	static Theme theme(String style) {
		return switch (style) {
			case "bone" -> new Theme(0xE8E0C8, 0x5B4A6B, ParticleTypes.ASH, ParticleTypes.SOUL, block(Blocks.BONE_BLOCK));
			case "arcane" -> new Theme(0x6E5BFF, 0xB8F3FF, ParticleTypes.ENCHANT, ParticleTypes.END_ROD, block(Blocks.AMETHYST_BLOCK));
			case "sand" -> new Theme(0xE3C46A, 0x8A6A2E, block(Blocks.SAND), ParticleTypes.SMALL_GUST, block(Blocks.SANDSTONE));
			case "ice" -> new Theme(0xA6E8FF, 0xFFFFFF, ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, block(Blocks.PACKED_ICE));
			case "jade" -> new Theme(BossAbilities.JADE, BossAbilities.GOLD, ParticleTypes.SMALL_GUST, ParticleTypes.HAPPY_VILLAGER, block(Blocks.MOSSY_COBBLESTONE));
			case "poison" -> new Theme(0x7BC63A, 0x2E5E1A, ParticleTypes.ITEM_SLIME, ParticleTypes.SNEEZE, block(Blocks.SLIME_BLOCK));
			case "solar" -> new Theme(BossAbilities.SOLAR, BossAbilities.GOLD, ParticleTypes.END_ROD, ParticleTypes.FLAME, block(Blocks.GOLD_BLOCK));
			case "lava" -> new Theme(0xFF5A12, 0x7A1A00, ParticleTypes.LAVA, ParticleTypes.FLAME, block(Blocks.MAGMA_BLOCK));
			case "soul" -> new Theme(CursedFx.SOUL, 0x0E3F4A, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL, block(Blocks.SOUL_SOIL));
			case "wither" -> new Theme(0x2B2B2B, 0x6A1020, ParticleTypes.LARGE_SMOKE, ParticleTypes.SQUID_INK, block(Blocks.BLACKSTONE));
			case "ghast" -> new Theme(0xFF8A2A, 0xFFF0C0, ParticleTypes.FLAME, ParticleTypes.LARGE_SMOKE, block(Blocks.NETHERRACK));
			case "nether" -> new Theme(0xB0102A, 0x3A0A4A, ParticleTypes.PORTAL, ParticleTypes.CRIMSON_SPORE, block(Blocks.CRIMSON_NYLIUM));
			default -> new Theme(CursedFx.PURPLE, CursedFx.BLOOD_BRIGHT, ParticleTypes.WITCH, ParticleTypes.SOUL, block(Blocks.DEEPSLATE));
		};
	}

	static ParticleOptions block(net.minecraft.world.level.block.Block block) {
		return new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
	}

	/**
	 * Warning line on the floor (a telegraphed line attack): a red dashed stroke from {@code from} to {@code to}, with a
	 * bright marker running along it as the strike nears ({@code k} = 0..1).
	 */
	static void lineWarning(RitualFx fx, Vec3 from, Vec3 to, double k, int age) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.5) {
			return;
		}
		int steps = (int) Math.max(4, Math.min(60, length * 1.6));
		for (int i = 0; i <= steps; i++) {
			if ((i + age / 2) % 3 == 2) {
				continue;
			}
			if (!fx.dot(RitualFx.dust(WARNING, 0.9F), from.add(delta.scale((double) i / steps)))) {
				return;
			}
		}
		Vec3 marker = from.add(delta.scale(Math.max(0, Math.min(1, k))));
		fx.dot(RitualFx.dust(0xFFE0A0, 1.4F), marker.add(0, 0.1, 0));
	}

	/** Warning cone on the floor: its two edges and its arc, the arc closing in as the strike nears. */
	static void coneWarning(RitualFx fx, Vec3 apex, double yaw, double halfAngle, double range, double k) {
		ParticleOptions red = RitualFx.dust(WARNING, 0.9F);
		Vec3 left = apex.add(Math.cos(yaw - halfAngle) * range, 0, Math.sin(yaw - halfAngle) * range);
		Vec3 right = apex.add(Math.cos(yaw + halfAngle) * range, 0, Math.sin(yaw + halfAngle) * range);
		fx.line(red, apex, left, 1.4);
		fx.line(red, apex, right, 1.4);
		fx.arc(red, apex, range, yaw - halfAngle, yaw + halfAngle, (int) Math.max(8, range * halfAngle * 2.2));
		double inner = range * Math.max(0, Math.min(1, k));
		if (inner > 0.8) {
			fx.arc(RitualFx.fade(WARNING, CursedFx.VOID, 0.8F), apex, inner, yaw - halfAngle, yaw + halfAngle, (int) Math.max(5, inner * halfAngle * 1.6));
		}
	}

	/**
	 * A ring of radius {@code radius} with its gaps: the wall in {@code wall}, each gap marked by two green posts (the way
	 * through).
	 */
	static void gapRing(RitualFx fx, Vec3 center, double radius, double[] gaps, double halfWidth, ParticleOptions wall, boolean posts) {
		int points = (int) Math.max(16, Math.min(110, radius * 7));
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			if (DreadRules.inGap(angle, gaps, halfWidth)) {
				continue;
			}
			if (!fx.dot(wall, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius)) {
				return;
			}
		}
		if (posts) {
			ParticleOptions green = RitualFx.dust(SAFE, 1.1F);
			for (double gap : gaps) {
				for (double side : new double[] {-halfWidth, halfWidth}) {
					double a = gap + side;
					fx.dot(green, center.x + Math.cos(a) * radius, center.y + 0.3, center.z + Math.sin(a) * radius);
					fx.dot(green, center.x + Math.cos(a) * radius, center.y + 0.9, center.z + Math.sin(a) * radius);
				}
			}
		}
	}

	/** Green circle of a safe spot (eye of the storm, warm dome), {@code k} = how well it is drawn already. */
	static void safeCircle(RitualFx fx, Vec3 center, double radius, double spin, double k) {
		ParticleOptions green = RitualFx.dust(SAFE, 1.0F);
		int points = (int) Math.max(14, radius * 8);
		fx.arc(green, center, radius, spin, spin + Math.PI * 2 * Math.max(0.1, Math.min(1, k)), points);
		if (k >= 1) {
			fx.ring(RitualFx.fade(SAFE, 0xFFFFFF, 0.7F), center.add(0, 0.6, 0), radius * 0.92, points / 2, -spin);
		}
	}

	/** A vertical column of particles (eruptions, pillars, geysers), {@code density} particles per block. */
	static void column(RitualFx fx, ParticleOptions particle, Vec3 base, double height, double spread, int density) {
		int n = (int) Math.max(2, Math.min(40, height * density));
		for (int i = 0; i < n; i++) {
			double y = height * i / n;
			if (!fx.burst(particle, base.x, base.y + y, base.z, 1, spread, 0.05, spread, 0.0)) {
				return;
			}
		}
	}

	/** Spikes jutting out of the floor at {@code at}: a few short columns of debris and the theme's colour. */
	static void spikes(RitualFx fx, Theme theme, Vec3 at, double height) {
		fx.burst(theme.debris(), at.x, at.y + 0.2, at.z, 6, 0.25, 0.2, 0.25, 0.25);
		for (int i = 0; i < 3; i++) {
			double h = height * (0.5 + 0.25 * i);
			fx.line(RitualFx.dust(theme.primary(), 1.2F), at.add((i - 1) * 0.25, 0, (1 - i) * 0.2), at.add((i - 1) * 0.12, h, (1 - i) * 0.1), 4);
		}
	}
}
