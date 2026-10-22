package dev.guildy.ruin;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Avatar of one running fight ({@link Encounter#avatar}): a cursed guardian summoned with a Nether star in the circle.
 * Its staging (a dark red sky: the red tint vanilla clients show near the world border, sent to the players of the arena
 * only and given back as soon as they leave or the fight ends; the boss music; its aura of crimson embers and soul fire),
 * its name and boss bar, its server-wide announcements and the "Coeur de l'Avatar" trophy. Its phases, final form and cursed
 * attacks are those of {@link CursedEncounter}; its own attacks run in {@link DreadEncounter}. Runtime only.
 */
final class AvatarEncounter {
	static final String TROPHY_KIND = "avatar_heart";
	static final int CRIMSON = 0xB0102A;
	static final int EMBER = 0xFF4A1A;
	/** A warning distance huge enough for every client to see the border's red tint wherever it stands. */
	private static final int RED_SKY_BLOCKS = Integer.MAX_VALUE;
	/** Players whose screen is tinted red right now. */
	private final Set<UUID> tinted = new HashSet<>();

	private static AvatarSettings settings() {
		return RuinModule.config().cursed.avatar;
	}

	// ================================================================== names

	/** "Avatar infernal » Seigneur des Cryptes" (its ascended final form once risen). */
	static Component name(String bossName, boolean finalForm) {
		return Lang.get(finalForm ? "ruin.avatar.name.final" : "ruin.avatar.name", bossName);
	}

	/** Boss bar: "☠ Avatar infernal — Seigneur des Cryptes — Menace X — Phase II — 4 joueurs — 52 340/64 000 ❤". */
	Component barName(Encounter encounter, float health, float max) {
		String name = Lang.colorize(encounter.def.boss.name);
		CursedEncounter cursed = encounter.cursed;
		if (cursed != null && cursed.transition != null) {
			return Lang.get("ruin.avatar.bar.transition", name, CursedRules.roman(cursed.transition.toPhase));
		}
		if (encounter.rising != null) {
			return Lang.get("ruin.avatar.bar.rising", name);
		}
		String threat = Encounters.threatLabel(encounter.threat);
		String title = encounter.risen ? Lang.raw("ruin.avatar.bar.final", name, threat)
				: Lang.raw("ruin.avatar.bar", name, threat, CursedRules.roman(cursed == null ? 1 : cursed.phase));
		if (encounter.party.group()) {
			title = Lang.raw("ruin.bar.group", title, encounter.party.players());
		}
		return Lang.get("ruin.bar", title, DreadRules.shown(health, encounter.vitality), DreadRules.shown(max, encounter.vitality));
	}

	/** Extra living escort mobs allowed (the rifts' Nether mobs). */
	int extraAlive() {
		return settings().riftMaxAlive;
	}

	// ================================================================== awakening

	/** The Avatar rises: titles, chat, a server-wide announcement, a crimson burst. */
	void announce(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present) {
		String ruinName = Lang.colorize(encounter.def.name);
		String bossName = Lang.colorize(encounter.def.boss.name);
		Component title = Lang.get("ruin.avatar.awaken.title");
		Component subtitle = Lang.get("ruin.avatar.awaken.subtitle", bossName);
		long health = DreadRules.shown(boss.getMaxHealth(), encounter.vitality);
		for (ServerPlayer player : present) {
			Msg.title(player, title, subtitle, 10, 90, 20);
			Msg.send(player, "ruin.avatar.awaken.chat", bossName, ruinName, health, Encounters.threatLabel(encounter.threat));
			if (encounter.party.group()) {
				Msg.send(player, "ruin.awaken.group", encounter.party.players(), GroupScaling.percent(encounter.party.health()),
						GroupScaling.percent(encounter.party.damage()));
			}
			Msg.sound(player, SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.5F);
			Msg.sound(player, SoundEvents.WITHER_SPAWN, 0.8F, 0.6F);
		}
		if (settings().announce) {
			level.getServer().getPlayerList().broadcastSystemMessage(Msg.prefixed(Lang.get("ruin.avatar.awaken.announce", bossName, ruinName)), false);
		}
		Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
		RitualFx fx = new RitualFx(level, at, 900, RuinModule.config().summon.viewDistance);
		fx.dot(RitualFx.flash(CRIMSON), at);
		fx.dot(RitualFx.flash(EMBER), at.add(0, 2, 0));
		fx.sphereBurst(RitualFx.fade(EMBER, CRIMSON, 1.8F), at, 0.5, 120, 0.6);
		fx.burst(ParticleTypes.SOUL_FIRE_FLAME, at, 60, 1.0, 0.1);
		fx.burst(ParticleTypes.LAVA, at, 30, 1.0, 0.3);
		fx.burst(ParticleTypes.CRIMSON_SPORE, at, 80, 3.0, 0.05);
		fx.flush();
		RuinFx.sound(level, at, SoundEvents.WITHER_SPAWN, 1.2F, 0.4F);
		RuinFx.sound(level, at, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.6F);
		syncSky(level, present);
	}

	// ================================================================== tick

	/** Every tick: the red sky of the players in the arena (every second), its aura, embers falling around the players. */
	void tick(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RitualFx fx, long now) {
		if (now % 20 == 0) {
			syncSky(level, present);
		}
		Vec3 feet = boss.position();
		double width = Math.max(0.6, boss.getBbWidth());
		double height = Math.max(0.6, boss.getBbHeight());
		if (now % 2 == 0) {
			int age = (int) (now / 2 % 1000);
			CursedFx.helix(fx, RitualFx.dust(EMBER, 1.3F), feet, width * 0.9 + 0.6, age, height + 1.0, 18);
			CursedFx.helix(fx, ParticleTypes.SOUL_FIRE_FLAME, feet, width * 0.9 + 0.6, age + 9, height + 1.0, 18);
			fx.burst(RitualFx.fade(EMBER, CRIMSON, 1.4F), feet.x, feet.y + height * 0.5, feet.z, 2, width * 0.5, height * 0.3, width * 0.5, 0.0);
		}
		if (now % 4 == 0) {
			// A crown of fire above its head and a burning sigil under its feet.
			double spin = now * 0.06;
			fx.ring(RitualFx.dust(EMBER, 1.0F), feet.add(0, height + 0.6, 0), Math.max(0.6, width * 0.45), 12, spin);
			fx.polygram(RitualFx.dust(CRIMSON, 1.0F), feet.add(0, 0.08, 0), width + 1.6, 5, 2, -spin, 1.0, 2.2);
			fx.ring(RitualFx.dust(CursedFx.SOUL, 0.9F), feet.add(0, 0.08, 0), width + 1.8, 22, spin);
		}
		// Ash and embers raining on the players: the sky burns.
		for (ServerPlayer player : present) {
			if ((now + player.getId()) % 3 == 0) {
				fx.burst(ParticleTypes.ASH, player.getX(), player.getY() + 4, player.getZ(), 6, 5, 2, 5, 0.0);
				fx.burst(RitualFx.dust(EMBER, 0.8F), player.getX(), player.getY() + 5, player.getZ(), 2, 5, 2, 5, 0.0);
				fx.burst(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 3, player.getZ(), 3, 5, 2, 5, 0.0);
			}
		}
		if (now % 160 == 0) {
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.5F, 0.3F);
		}
	}

	/** The red sky for the players of the arena, given back to those who left. */
	void syncSky(ServerLevel level, List<ServerPlayer> present) {
		if (!settings().redSky) {
			restore(level);
			return;
		}
		Set<UUID> here = new HashSet<>();
		WorldBorder red = new WorldBorder();
		red.setWarningBlocks(RED_SKY_BLOCKS);
		for (ServerPlayer player : present) {
			here.add(player.getUUID());
			if (tinted.add(player.getUUID())) {
				player.connection.send(new ClientboundSetBorderWarningDistancePacket(red));
			}
		}
		for (UUID uuid : new ArrayList<>(tinted)) {
			if (!here.contains(uuid)) {
				tinted.remove(uuid);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
				if (player != null) {
					player.connection.send(new ClientboundSetBorderWarningDistancePacket(player.level().getWorldBorder()));
				}
			}
		}
	}

	/** Every tinted player gets the real world border back. */
	void restore(ServerLevel level) {
		for (UUID uuid : tinted) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
			if (player != null) {
				player.connection.send(new ClientboundSetBorderWarningDistancePacket(player.level().getWorldBorder()));
			}
		}
		tinted.clear();
	}

	/** Players whose sky is red (tests). */
	boolean tinted(UUID player) {
		return tinted.contains(player);
	}

	// ================================================================== end

	/** The Avatar falls: titles, the server-wide announcement, a burst of embers and freed souls; the sky clears. */
	void victory(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present) {
		String bossName = Lang.colorize(encounter.def.boss.name);
		String ruinName = Lang.colorize(encounter.def.name);
		for (ServerPlayer player : present) {
			Msg.title(player, Lang.get("ruin.avatar.victory.title"), Lang.get("ruin.avatar.victory.subtitle", bossName), 10, 100, 30);
			Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.6F);
			Msg.sound(player, SoundEvents.ENDER_DRAGON_DEATH, 0.4F, 1.0F);
		}
		Vec3 at = boss.position().add(0, boss.getBbHeight() / 2, 0);
		RitualFx fx = new RitualFx(level, at, 1200, RuinModule.config().summon.viewDistance);
		fx.dot(RitualFx.flash(0xFFFFFF), at);
		fx.dot(RitualFx.flash(EMBER), at.add(0, 2, 0));
		fx.sphereBurst(RitualFx.fade(EMBER, 0xFFE9A0, 2.0F), at, 0.6, 160, 0.7);
		fx.burst(ParticleTypes.TOTEM_OF_UNDYING, at, 140, 0.9, 0.8);
		fx.burst(ParticleTypes.SOUL, at, 80, 1.4, 0.15);
		fx.burst(ParticleTypes.LAVA, at, 30, 1.2, 0.3);
		fx.flush();
		RuinFx.play(level, (lvl, age) -> {
			RuinFx.helix(lvl, ParticleTypes.SOUL_FIRE_FLAME, boss.position(), 1.4 + age * 0.06, age, 8.0, 30);
			RuinFx.helix(lvl, ParticleTypes.END_ROD, boss.position(), 1.0 + age * 0.05, age + 15, 10.0, 30);
			return age < 60;
		});
		RuinFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.8F);
		RuinFx.sound(level, at, SoundEvents.TOTEM_USE, 2.0F, 0.6F);
		if (settings().announce) {
			String names = encounter.names.isEmpty() ? Lang.raw("ruin.victory.someone") : String.join(", ", encounter.names.values());
			level.getServer().getPlayerList().broadcastSystemMessage(Msg.prefixed(Lang.get("ruin.avatar.victory.announce", names, bossName, ruinName)), false);
		}
		restore(level);
	}

	/** Everyone left or died: the Avatar returns to the Nether, the sky clears. */
	void vanish(ServerLevel level, Encounter encounter, boolean announce) {
		restore(level);
		if (announce) {
			for (ServerPlayer player : Encounters.present(level, encounter.ruin, RuinModule.config().encounter.presenceMargin * 2)) {
				Msg.send(player, "ruin.avatar.vanish", Lang.colorize(encounter.def.boss.name));
			}
		}
	}

	// ================================================================== the Nether star

	/** The item that summons the Avatar (a plain one: never a Guildy item such as its trophy). */
	static boolean isStar(ItemStack stack) {
		if (stack.isEmpty() || GuildyItems.isSpecial(stack)) {
			return false;
		}
		Item item = ItemUtil.item(settings().item);
		return item != null && stack.is(item);
	}

	/** The Nether star lying on the ground of the ritual circle (within {@code radius} of its center), the closest one, or null. */
	static @Nullable ItemEntity star(ServerLevel level, Vec3 ground, double radius) {
		AABB box = new AABB(ground.x - radius, ground.y - 3, ground.z - radius, ground.x + radius,
				ground.y + 6, ground.z + radius);
		ItemEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box,
				e -> e.isAlive() && isStar(e.getItem()))) {
			double dx = item.getX() - ground.x;
			double dz = item.getZ() - ground.z;
			double dist = dx * dx + dz * dz;
			if (dist <= radius * radius && dist < bestDist) {
				bestDist = dist;
				best = item;
			}
		}
		return best;
	}

	/** The star is consumed (one of its stack) by the rising Avatar: a stream of embers from the star into it. */
	static void consume(ServerLevel level, ItemEntity star, Vec3 into, List<ServerPlayer> witnesses) {
		Vec3 from = star.position().add(0, 0.3, 0);
		ItemStack stack = star.getItem().copy();
		stack.shrink(1);
		if (stack.isEmpty()) {
			star.discard();
		} else {
			star.setItem(stack);
		}
		RitualFx fx = new RitualFx(level, from, 400, RuinModule.config().summon.viewDistance);
		fx.dot(RitualFx.flash(0xFFFFFF), from);
		fx.burst(ParticleTypes.END_ROD, from, 30, 0.3, 0.2);
		for (int i = 0; i < 24; i++) {
			fx.dot(RitualFx.trail(into, i % 2 == 0 ? EMBER : CRIMSON, 12 + i % 8), from.x, from.y + i * 0.02, from.z);
		}
		fx.flush();
		RuinFx.sound(level, from, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
		RuinFx.sound(level, from, SoundEvents.WITHER_SPAWN, 1.0F, 1.6F);
		for (ServerPlayer player : witnesses) {
			Msg.send(player, "ruin.avatar.star.consumed");
		}
	}

	// ================================================================== trophy

	/** The "Coeur de l'Avatar": a collector's trophy shaped like a Nether star (no other use: not a star, no beacon). */
	static ItemStack trophy(Encounter encounter) {
		ItemStack stack = new ItemStack(Items.HEART_OF_THE_SEA);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("nether_star"));
		stack.set(DataComponents.ITEM_NAME, Component.literal(Lang.raw("ruin.avatar.trophy.name")).withStyle(style -> style.withItalic(false)));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		String victors = encounter.names.isEmpty() ? Lang.raw("ruin.victory.someone") : String.join(", ", encounter.names.values());
		List<Component> lore = new ArrayList<>();
		for (String line : Lang.raw("ruin.avatar.trophy.lore", Lang.colorize(encounter.def.boss.name), Lang.colorize(encounter.def.name),
				Encounters.threatLabel(encounter.threat), victors).split("\n")) {
			lore.add(Component.literal(line).withStyle(style -> style.withItalic(false)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		CompoundTag data = new CompoundTag();
		data.putString("ruin", encounter.ruin.ruinId);
		GuildyItems.tag(stack, TROPHY_KIND, data);
		return stack;
	}
}
