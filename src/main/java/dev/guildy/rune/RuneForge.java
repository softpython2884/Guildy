package dev.guildy.rune;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.structure.TextUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rune inscription for the blacksmith villagers (public API of the rune module): which pieces of an armor stand may
 * receive a rune, the draw, and the inscription itself on the stand (the piece never leaves it) with its visuals, the
 * guild message and the server announcement of a mythic rune.
 */
public final class RuneForge {
	private RuneForge() {
	}

	/** Runes are on and the blacksmiths may inscribe some (a daily cap above 0). */
	public static boolean active() {
		RuneConfig config = RuneModule.config();
		return config.enabled && config.inscription.maxPerDay > 0;
	}

	public static RuneConfig.Inscription settings() {
		return RuneModule.config().inscription;
	}

	/** May this piece receive a rune now: an armor piece without rune, and at least one rune fits it. */
	public static boolean canReceive(ItemStack stack) {
		if (!RuneItems.canReceive(stack)) {
			return false;
		}
		EquipmentSlot slot = RuneItems.armorSlot(stack);
		RuneConfig config = RuneModule.config();
		return slot != null && !RuneRules.candidates(config.runes, slot.getSerializedName(), RuneItems.itemId(stack), config.mythicItems).isEmpty();
	}

	/** Armor slots of the stand holding a piece that may receive a rune. */
	public static List<EquipmentSlot> receivers(ArmorStand stand) {
		List<EquipmentSlot> slots = new ArrayList<>();
		for (EquipmentSlot slot : RunePowers.ARMOR) {
			if (canReceive(stand.getItemBySlot(slot))) {
				slots.add(slot);
			}
		}
		return slots;
	}

	/** The rune drawn for that piece (tier, then rune; no chance roll here), null when none fits. */
	public static RuneConfig.@Nullable RuneDef roll(ItemStack stack, RandomSource random) {
		EquipmentSlot slot = RuneItems.armorSlot(stack);
		if (slot == null) {
			return null;
		}
		return RuneRules.pick(RuneModule.config(), slot.getSerializedName(), RuneItems.itemId(stack), random.nextDouble(), random.nextDouble());
	}

	/** Material used for each rune (null = free). */
	public static @Nullable Item material() {
		String id = settings().materialId();
		return id.isEmpty() ? null : ItemUtil.item(id);
	}

	/** Materials used for each rune (0 = free). */
	public static int materialCount() {
		return material() == null ? 0 : settings().materialCount;
	}

	/** Game day used for the daily cap. */
	public static long day(ServerLevel level) {
		return RuneRules.day(level.getOverworldClockTime());
	}

	/**
	 * Inscribes {@code def} on the piece in {@code slot} of the stand (in place): visuals and sounds around the stand, a
	 * message to the guild, a server announcement for a mythic rune.
	 *
	 * @return false when the piece cannot receive it any more
	 */
	public static boolean inscribe(ServerLevel level, @Nullable Guild guild, ArmorStand stand, EquipmentSlot slot, RuneConfig.RuneDef def,
			String smith) {
		ItemStack piece = stand.getItemBySlot(slot);
		if (!RuneItems.canReceive(piece)) {
			return false;
		}
		RuneItems.inscribe(piece, def);
		inscriptionFx(level, stand, slot, def);
		RuneTier tier = def.tier();
		Component item = piece.getDisplayName();
		String name = tier.color + def.name;
		String tierName = tier.color + Lang.raw(tier.langKey());
		if (guild != null) {
			Msg.guildRaw(guild, TextUtil.lang("rune.inscribed", smith, tierName, name, item));
		}
		if (tier == RuneTier.MYTHIC && settings().announceMythic) {
			Component message = Msg.prefixed(TextUtil.lang("rune.announce.mythic", smith, guild == null ? "?" : guild.name, name, item));
			for (ServerPlayer player : GuildyServer.players()) {
				player.sendSystemMessage(message);
				Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
			}
			if (guild != null) {
				Msg.guildTitle(guild, Lang.get("rune.mythic.title"), Lang.get("rune.mythic.subtitle", name));
			}
		}
		return true;
	}

	/** Height of an armor slot's piece on a standing armor stand. */
	static double slotHeight(ArmorStand stand, EquipmentSlot slot) {
		double h = stand.getBbHeight();
		return switch (slot) {
			case HEAD -> h * 0.9;
			case CHEST -> h * 0.65;
			case LEGS -> h * 0.4;
			default -> h * 0.1;
		};
	}

	/**
	 * Enchanting glyphs flying into the piece, two strands of the tier's colour spiralling up the stand, pulsing rings,
	 * then a flash; a mythic rune adds a column of light and a burst of its element (flames or dragon fire).
	 */
	static void inscriptionFx(ServerLevel level, ArmorStand stand, EquipmentSlot slot, RuneConfig.RuneDef def) {
		RuneTier tier = def.tier();
		int color = RuneFx.tierColor(tier);
		double height = slotHeight(stand, slot);
		double standHeight = stand.getBbHeight();
		Vec3 origin = stand.position();
		int duration = switch (tier) {
			case COMMON -> 30;
			case RARE -> 40;
			case MYTHIC -> 70;
		};
		RunePower power = def.power();
		level.playSound(null, origin.x, origin.y + height, origin.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, origin.x, origin.y + height, origin.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (tier == RuneTier.MYTHIC) {
			level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.0F);
		}
		RuneFx.play(level, duration + 12, (lvl, age) -> {
			Vec3 base = stand.isAlive() ? stand.position() : origin;
			Vec3 piece = base.add(0, height, 0);
			if (age < duration) {
				for (int i = 0; i < (tier == RuneTier.COMMON ? 3 : 5); i++) {
					double yaw = lvl.getRandom().nextDouble() * Math.PI * 2;
					double pitch = (lvl.getRandom().nextDouble() - 0.3) * 1.2;
					Vec3 from = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).scale(1.6);
					RuneFx.move(lvl, ParticleTypes.ENCHANT, piece, from);
				}
				double t = age / (double) duration;
				for (int strand = 0; strand < 2; strand++) {
					double angle = age * 0.35 + Math.PI * strand;
					RuneFx.dot(lvl, RuneFx.fade(color, 0xFFFFFF, 1.0F),
							base.add(Math.cos(angle) * 0.75, t * standHeight * 1.1, Math.sin(angle) * 0.75));
				}
				if (age % 10 == 0) {
					RuneFx.ring(lvl, RuneFx.fade(color, 0xFFFFFF, 0.9F), piece, 0.6, 14, age * 0.1);
					RuneFx.ring(lvl, ParticleTypes.ENCHANT, base.add(0, 0.1, 0), 1.1, 16, age * 0.05);
				}
				if (tier == RuneTier.MYTHIC && age % 3 == 0) {
					for (int k = 0; k < 6; k++) {
						RuneFx.dot(lvl, ParticleTypes.END_ROD, base.add(0, standHeight + 0.4 + k * 0.6 * t, 0));
					}
					RuneFx.ring(lvl, element(power), base.add(0, 0.15, 0), 1.6 - t * 0.8, 10, age * 0.2);
				}
				return true;
			}
			if (age == duration) {
				RuneFx.burst(lvl, RuneFx.flash(color), piece, 1, 0, 0);
				RuneFx.burst(lvl, RuneFx.dust(color, 1.3F), piece, 24, 0.35, 0.0);
				RuneFx.burst(lvl, ParticleTypes.END_ROD, piece, tier == RuneTier.COMMON ? 6 : 14, 0.3, 0.08);
				if (tier != RuneTier.COMMON) {
					RuneFx.burst(lvl, ParticleTypes.TOTEM_OF_UNDYING, piece, tier == RuneTier.MYTHIC ? 80 : 30, 0.4, 0.4);
				}
				if (tier == RuneTier.MYTHIC) {
					RuneFx.burst(lvl, element(power), piece, 40, 0.6, 0.08);
					lvl.playSound(null, piece.x, piece.y, piece.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2F, 1.0F);
					if (power == RunePower.DRAGON) {
						lvl.playSound(null, piece.x, piece.y, piece.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 0.6F, 1.3F);
					} else {
						lvl.playSound(null, piece.x, piece.y, piece.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0F, 0.7F);
					}
				}
				lvl.levelEvent(LevelEvent.SOUND_ANVIL_USED, stand.blockPosition(), 0);
				lvl.playSound(null, piece.x, piece.y, piece.z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, tier == RuneTier.COMMON ? 1.6F : 1.2F);
				return true;
			}
			if (age % 3 == 0) {
				RuneFx.burst(lvl, RuneFx.dust(color, 0.8F), piece, 3, 0.4, 0.0);
			}
			return age < duration + 10;
		});
	}

	/** Particle of a mythic rune's element: flames (Phénix), dragon fire (Dragon), else glyphs. */
	private static ParticleOptions element(@Nullable RunePower power) {
		if (power == RunePower.PHOENIX) {
			return ParticleTypes.FLAME;
		}
		if (power == RunePower.DRAGON) {
			return PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
		}
		return ParticleTypes.ENCHANT;
	}
}
