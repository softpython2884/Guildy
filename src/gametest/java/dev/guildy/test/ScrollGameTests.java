package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.villager.VillagerManager;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Spell scrolls: items and drops, then every spell cast through the real game paths (right click = use item event, the
 * vanilla channel completed by ticking the player) by a survival player standing near monsters, a villager of their guild
 * and a player of no guild: monsters are hurt / affected, the villager and the other player are untouched, exactly one
 * scroll is consumed and the cooldown of its group starts. Failures (no target, cooldown, no guild, interrupted channel)
 * keep the scroll; creative casts without consuming; a custom_data-only stack is completed in the inventory.
 */
public class ScrollGameTests {
	private static final String ENV = "guildy-test:day";
	/** nextDouble() == 0: every chance passes. */
	private static final RandomGenerator LUCKY = () -> 0L;

	/** A caster in a guild, one villager of that guild, a player of no guild, monsters. */
	private record Scene(ServerLevel level, BlockPos ground, ServerPlayer caster, Guild guild, Villager villager, ServerPlayer stranger,
			List<Husk> husks, float villagerHealth, float strangerHealth) {
	}

	/**
	 * Builds the scene: the guild heart 10 blocks west, the caster at {@code ground} facing east, the villager and the other
	 * player next to the monsters (proof that spells spare them), monsters at the given offsets east of the caster.
	 */
	private static Scene scene(GameTestHelper helper, String prefix, BlockPos villagerOffset, BlockPos strangerOffset, int... huskEast) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 13);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(10));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(10), prefix);
		face(caster, level, ground);
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		BlockPos v = ground.offset(villagerOffset);
		villager.snapTo(v.getX() + 0.5, v.getY() + 1, v.getZ() + 0.5, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setPersistenceRequired();
		villager.addTag(VillagerManager.TAG_VILLAGER);
		guild.villagers.members.put(villager.getUUID(), new VillagerRecord(villager.getUUID(), "Testeur"));
		level.addFreshEntity(villager);
		BlockPos s = ground.offset(strangerOffset);
		ServerPlayer stranger = TestSupport.survivor(helper, s);
		List<Husk> husks = new ArrayList<>();
		for (int east : huskEast) {
			husks.add(husk(level, ground.east(east)));
		}
		return new Scene(level, ground, caster, guild, villager, stranger, husks, villager.getHealth(), stranger.getHealth());
	}

	/** Caster standing on {@code ground}, looking east, horizontally. */
	private static void face(ServerPlayer player, ServerLevel level, BlockPos ground) {
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
	}

	private static Husk husk(ServerLevel level, BlockPos ground) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		return husk;
	}

	private static boolean hurt(LivingEntity entity) {
		return entity.isRemoved() || entity.isDeadOrDying() || entity.getHealth() < entity.getMaxHealth();
	}

	/** Right click with {@code count} scrolls of {@code id} in the main hand; returns the stack in hand. */
	private static ItemStack rightClick(GameTestHelper helper, ServerPlayer player, String id, int count, boolean expectStart) {
		ItemStack stack = ScrollDrops.create(id, count);
		helper.assertTrue(ScrollItems.isScroll(stack) && stack.getCount() == count, "scroll " + id + " created");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && player.isUsingItem(), id + ": incantation started, got " + result);
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), id + ": refused, got " + result);
		}
		return player.getMainHandItem();
	}

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	/** Casts {@code id} (2 in hand): exactly one consumed, the cooldown started. */
	private static ItemStack cast(GameTestHelper helper, Scene scene, String id) {
		ItemStack stack = rightClick(helper, scene.caster(), id, 2, true);
		channel(scene.caster());
		helper.assertFalse(scene.caster().isUsingItem(), id + ": channel completed");
		ItemStack hand = scene.caster().getMainHandItem();
		helper.assertTrue(hand == stack && hand.getCount() == 1, id + ": exactly one scroll consumed, left " + hand.getCount());
		helper.assertTrue(scene.caster().getCooldowns().isOnCooldown(hand), id + ": cooldown started");
		return hand;
	}

	/** The guild villager and the other player kept their health and got no effect, no fire. */
	private static void untouched(GameTestHelper helper, Scene scene) {
		helper.assertTrue(scene.villager().isAlive() && scene.villager().getHealth() >= scene.villagerHealth(), "the guild villager is not hurt");
		helper.assertTrue(scene.stranger().getHealth() >= scene.strangerHealth(), "the other player is not hurt");
		for (LivingEntity spared : List.of(scene.villager(), scene.stranger())) {
			helper.assertTrue(spared.getActiveEffects().isEmpty(), "no effect on " + spared.getType() + ": " + spared.getActiveEffects());
			helper.assertTrue(spared.getRemainingFireTicks() <= 0, "not on fire: " + spared.getType());
		}
	}

	private static void allHurt(GameTestHelper helper, Scene scene, String what) {
		for (Husk husk : scene.husks()) {
			helper.assertTrue(hurt(husk), what + ": husk at " + husk.blockPosition() + " not hurt yet (" + husk.getHealth() + ")");
		}
	}

	private static void cleanup(Scene scene) {
		for (Husk husk : scene.husks()) {
			husk.discard();
		}
		scene.villager().discard();
		scene.guild().villagers.members.remove(scene.villager().getUUID());
		GuildManager.disband(scene.guild());
	}

	private static boolean hasEffect(LivingEntity entity, Holder<MobEffect> effect) {
		return entity.hasEffect(effect);
	}

	// ================================================================== items and drops

	@GameTest(maxTicks = 80)
	public void scrollsCarryTheirComponents(GameTestHelper helper) {
		ScrollConfig cfg = ScrollModule.config();
		helper.assertTrue(cfg.usable().size() == 12, "twelve default scrolls");
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			ItemStack stack = ScrollDrops.create(def.id, 3);
			helper.assertTrue(def.id.equals(ScrollItems.idOf(stack)), def.id + ": custom_data guildy.kind=scroll, id");
			helper.assertTrue(GuildyItems.is(stack, ScrollItems.KIND), "Guildy special item (never sold to the shop)");
			helper.assertTrue(stack.getCount() == 3 && stack.getMaxStackSize() == def.maxStack, def.id + ": count and max stack size");
			helper.assertTrue(stack.has(DataComponents.CONSUMABLE), def.id + ": vanilla channel animation");
			helper.assertTrue(stack.getUseDuration(null) == def.channelTicks(), def.id + ": channel time");
			helper.assertTrue(stack.get(DataComponents.LORE).lines().size() >= 8, def.id + ": rarity, story, spell, cast lines");
			helper.assertTrue(stack.get(DataComponents.ITEM_NAME).getString().contains(def.name), def.id + ": name");
			UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
			helper.assertTrue(cooldown != null && cooldown.cooldownGroup().map(ScrollItems.cooldownGroup(def)::equals).orElse(false),
					def.id + ": shared cooldown group");
			helper.assertFalse(stack.has(DataComponents.PROVIDES_BANNER_PATTERNS), def.id + ": no loom pattern");
			helper.assertTrue(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), def.id + ": glint");
			StringBuilder lore = new StringBuilder();
			for (Component line : stack.get(DataComponents.LORE).lines()) {
				lore.append(line.getString()).append('|');
			}
			helper.assertFalse(lore.indexOf("{") >= 0, def.id + ": every placeholder replaced: " + lore);
		}
		// (The luckiest ruin boss roll gives the grimoire: see GrimoireGameTests.)
		ItemStack lucky = ScrollDrops.roll("boss", LUCKY);
		helper.assertTrue(ScrollItems.isScroll(lucky), "a lucky horde boss roll gives scrolls");
		helper.assertTrue(ScrollDrops.roll("no_such_source", LUCKY).isEmpty(), "unknown source: nothing");
		helper.assertTrue(ScrollDrops.create("no_such_scroll", 1).isEmpty(), "unknown id: nothing");
		ItemStack rare = ScrollDrops.create("rappel", 1);
		ItemStack legendary = ScrollDrops.create("pluie_de_meteores", 1);
		helper.assertTrue(ScrollModule.tradePrice(rare, 1) == 0 && ScrollModule.tradePrice(rare, 2) > 0, "rare scrolls from the tier II hub");
		helper.assertTrue(ScrollModule.tradePrice(legendary, 2) == 0 && ScrollModule.tradePrice(legendary, 3) > ScrollModule.tradePrice(rare, 3),
				"legendary scrolls from tier III, dearer");
		helper.assertTrue(ScrollModule.tradePrice(new ItemStack(Items.PAPER), 3) == 0, "plain paper is no scroll");

		// Admin commands.
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin scroll give @s blizzard 3");
		helper.assertTrue(count(player, "blizzard") == 3, "admin give");
		TestSupport.console(level, "guild admin scroll list");
		helper.succeed();
	}

	/** A loot table can hold a scroll with only its custom data: it is completed as soon as it is in a player's inventory. */
	@GameTest(maxTicks = 80)
	public void customDataOnlyScrollIsCompleted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		ItemStack raw = new ItemStack(Items.PAPER, 20);
		CompoundTag extra = new CompoundTag();
		extra.putString("id", "rappel");
		GuildyItems.tag(raw, ScrollItems.KIND, extra);
		player.getInventory().add(raw);
		ScrollConfig.ScrollDef def = ScrollModule.config().scroll("rappel");
		helper.succeedWhen(() -> {
			int total = 0;
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!"rappel".equals(ScrollItems.idOf(stack))) {
					continue;
				}
				helper.assertTrue(stack.is(Items.GLOBE_BANNER_PATTERN), "rebuilt on the definition's base item: " + stack);
				helper.assertTrue(stack.has(DataComponents.CONSUMABLE) && stack.has(DataComponents.USE_COOLDOWN), "components added");
				helper.assertTrue(stack.get(DataComponents.ITEM_NAME).getString().contains("Rappel"), "named");
				helper.assertTrue(stack.getCount() <= def.maxStack, "within the max stack size");
				total += stack.getCount();
			}
			helper.assertTrue(total == 20, "every scroll kept: " + total);
		});
	}

	// ================================================================== failures keep the scroll

	@GameTest(environment = ENV, maxTicks = 100)
	public void failuresKeepTheScroll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer loner = TestSupport.survivor(helper, ground);
		face(loner, level, ground);

		// No monster in range: the lightning storm is refused, nothing consumed, no cooldown.
		ItemStack storm = rightClick(helper, loner, "tempete_de_foudre", 2, false);
		helper.assertTrue(storm.getCount() == 2 && !loner.getCooldowns().isOnCooldown(storm), "storm kept, no cooldown");
		// No guild: no recall.
		ItemStack recall = rightClick(helper, loner, "rappel", 1, false);
		helper.assertTrue(recall.getCount() == 1 && !loner.getCooldowns().isOnCooldown(recall), "recall kept");
		// The monster vanishes during the incantation: refused at the end, nothing consumed.
		Husk husk = husk(level, ground.east(5));
		ItemStack second = rightClick(helper, loner, "tempete_de_foudre", 2, true);
		husk.discard();
		channel(loner);
		helper.assertTrue(second.getCount() == 2 && !loner.getCooldowns().isOnCooldown(second), "no target at the end: storm kept");
		// Released before the end: interrupted, nothing consumed.
		ItemStack blessing = rightClick(helper, loner, "benediction", 1, true);
		loner.releaseUsingItem();
		helper.assertFalse(loner.isUsingItem(), "released");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(blessing.getCount() == 1 && !loner.getCooldowns().isOnCooldown(blessing), "interrupted: blessing kept");
			// Cast, then on cooldown: the second scroll is kept.
			ItemStack two = rightClick(helper, loner, "benediction", 2, true);
			channel(loner);
			helper.assertTrue(two.getCount() == 1 && loner.getCooldowns().isOnCooldown(two), "first blessing cast");
			loner.setItemInHand(InteractionHand.MAIN_HAND, two);
			InteractionResult again = UseItemCallback.EVENT.invoker().interact(loner, level, InteractionHand.MAIN_HAND);
			helper.assertTrue(again == InteractionResult.FAIL && two.getCount() == 1 && !loner.isUsingItem(), "on cooldown: refused, kept");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void creativeCastsWithoutConsuming(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		ServerPlayer builder = TestSupport.player(helper, ground);
		ItemStack stack = rightClick(helper, builder, "benediction", 1, true);
		channel(builder);
		helper.assertTrue(builder.getMainHandItem() == stack && stack.getCount() == 1, "creative: not consumed");
		helper.assertTrue(builder.getCooldowns().isOnCooldown(stack), "cooldown in creative too (casting.creativeCooldown)");
		// The blessing lands on the first tick of its effect.
		helper.succeedWhen(() -> helper.assertTrue(builder.hasEffect(MobEffects.REGENERATION), "but cast (regeneration)"));
	}

	// ================================================================== the twelve spells

	@GameTest(environment = ENV, maxTicks = 220)
	public void meteorShower(GameTestHelper helper) {
		ScrollConfig.ScrollDef def = ScrollModule.config().scroll("pluie_de_meteores");
		Double radius = def.params.get("radius");
		// Every meteor falls close to the aimed monster (deterministic test).
		def.params.put("radius", 2.0);
		Scene scene = scene(helper, "Meteores", new BlockPos(9, 0, 1), new BlockPos(9, 0, -1), 8);
		try {
			cast(helper, scene, "pluie_de_meteores");
		} finally {
			def.params.put("radius", radius);
		}
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "meteors");
			helper.assertTrue(scene.husks().getFirst().isRemoved() || scene.husks().getFirst().getRemainingFireTicks() > 0
					|| scene.husks().getFirst().isDeadOrDying(), "the husk burns");
			untouched(helper, scene);
			BlockPos impact = scene.ground().east(8).above();
			helper.assertFalse(scene.level().getBlockState(impact).is(Blocks.FIRE), "no fire block");
			cleanup(scene);
		});
	}

	/**
	 * Balance (Guildy 1.10): a meteor shower on an armored guardian (armor 30, toughness 20: four fifths of a vanilla hit
	 * absorbed) and its escort. Spell damage ignores armor and two meteors out of three follow a monster: the escort dies,
	 * the guardian takes several full meteors.
	 */
	@GameTest(environment = ENV, maxTicks = 260)
	public void meteorShowerDentsAnArmoredGuardian(GameTestHelper helper) {
		Scene scene = scene(helper, "Meteores lourds", new BlockPos(6, 0, 2), new BlockPos(6, 0, -2), 10, 14);
		Zombie guardian = EntityTypes.ZOMBIE.create(scene.level(), EntitySpawnReason.COMMAND);
		BlockPos at = scene.ground().east(6);
		guardian.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 90.0F, 0.0F);
		guardian.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		guardian.getAttribute(Attributes.ARMOR).setBaseValue(30);
		guardian.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
		guardian.setHealth(1000);
		// No burning damage: only the meteors count.
		guardian.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		guardian.setNoAi(true);
		guardian.setPersistenceRequired();
		scene.level().addFreshEntity(guardian);
		helper.assertTrue(guardian.getArmorValue() >= 30, "armored guardian, armor " + guardian.getArmorValue());
		double meteor = ScrollModule.config().scroll("pluie_de_meteores").param("damage");
		int count = ScrollModule.config().scroll("pluie_de_meteores").intParam("count");
		helper.assertTrue(meteor >= 28, "Guildy 1.10 meteor damage, got " + meteor);
		cast(helper, scene, "pluie_de_meteores");
		helper.startSequence()
				.thenIdle(110)
				.thenExecute(() -> {
					for (Husk husk : scene.husks()) {
						helper.assertTrue(husk.isRemoved() || husk.isDeadOrDying(), "escort husk at " + husk.blockPosition() + " killed, health "
								+ husk.getHealth());
					}
					double dealt = guardian.getMaxHealth() - guardian.getHealth();
					// Logged with the chat of the test players (balance figures).
					scene.caster().sendSystemMessage(Component.literal("meteor shower: " + dealt + " health points through armor "
							+ guardian.getArmorValue() + " (" + count + " meteors of " + meteor + ")"));
					// At least three full meteors (seven are called, two of them on the escort); never more than every meteor.
					helper.assertTrue(dealt >= 3 * meteor - 0.01, "several full meteors through the armor, dealt " + dealt);
					helper.assertTrue(dealt <= count * meteor + 0.01, "at most every meteor once, dealt " + dealt);
					untouched(helper, scene);
					guardian.discard();
					cleanup(scene);
				})
				.thenSucceed();
	}

	@GameTest(environment = ENV, maxTicks = 120)
	public void chainStorm(GameTestHelper helper) {
		Scene scene = scene(helper, "Foudre", new BlockPos(6, 0, 1), new BlockPos(9, 0, 1), 5, 8, 11);
		cast(helper, scene, "tempete_de_foudre");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "lightning chain");
			for (Husk husk : scene.husks()) {
				helper.assertTrue(husk.isRemoved() || husk.hasEffect(MobEffects.SLOWNESS), "stunned");
				helper.assertTrue(husk.getRemainingFireTicks() <= 0, "visual lightning: no fire");
			}
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 140)
	public void blizzard(GameTestHelper helper) {
		Scene scene = scene(helper, "Blizzard", new BlockPos(8, 0, 2), new BlockPos(8, 0, -2), 8, 10);
		cast(helper, scene, "blizzard");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "blizzard");
			for (Husk husk : scene.husks()) {
				helper.assertTrue(husk.isRemoved() || husk.hasEffect(MobEffects.SLOWNESS) && husk.getTicksFrozen() > 0, "slowed and frozen");
			}
			helper.assertTrue(scene.villager().getTicksFrozen() == 0 && scene.stranger().getTicksFrozen() == 0, "allies not frozen");
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 120)
	public void solarNova(GameTestHelper helper) {
		Scene scene = scene(helper, "Nova", new BlockPos(2, 0, 1), new BlockPos(-2, 0, 0), 4, 7);
		cast(helper, scene, "nova_solaire");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "nova");
			untouched(helper, scene);
			helper.assertTrue(scene.caster().getRemainingFireTicks() <= 0, "the caster does not burn");
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void singularity(GameTestHelper helper) {
		Scene scene = scene(helper, "Singularite", new BlockPos(9, 0, 2), new BlockPos(7, 0, -2), 8, 11);
		cast(helper, scene, "singularite");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "implosion");
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 120)
	public void aegis(GameTestHelper helper) {
		Scene scene = scene(helper, "Egide", new BlockPos(2, 0, 1), new BlockPos(-2, 0, 0), 3);
		cast(helper, scene, "egide");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "dome");
			helper.assertTrue(scene.caster().hasEffect(MobEffects.ABSORPTION) && scene.caster().hasEffect(MobEffects.RESISTANCE),
					"the caster is shielded");
			helper.assertTrue(scene.villager().hasEffect(MobEffects.RESISTANCE), "the guild villager is shielded");
			helper.assertTrue(scene.villager().getHealth() >= scene.villagerHealth(), "the villager is not hurt");
			helper.assertFalse(hasEffect(scene.stranger(), MobEffects.RESISTANCE) || hasEffect(scene.stranger(), MobEffects.ABSORPTION),
					"no buff for a player of another guild");
			helper.assertTrue(scene.stranger().getHealth() >= scene.strangerHealth(), "the other player is not hurt");
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void recall(GameTestHelper helper) {
		Scene scene = scene(helper, "Rappel", new BlockPos(3, 0, 3), new BlockPos(0, 0, 2));
		ServerPlayer member = TestSupport.survivor(helper, scene.ground().south(3));
		GuildManager.addMember(scene.guild(), member.getUUID(), "membre");
		Vec3 strangerBefore = scene.stranger().position();
		cast(helper, scene, "rappel");
		BlockPos heart = scene.guild().heart;
		helper.assertTrue(heart != null, "the guild has a heart");
		Vec3 home = Vec3.atBottomCenterOf(heart);
		helper.assertTrue(scene.caster().position().distanceTo(home) < 3, "the caster is back home: " + scene.caster().position());
		helper.assertTrue(member.position().distanceTo(home) < 3, "the guild member came along: " + member.position());
		helper.assertTrue(scene.stranger().position().distanceTo(strangerBefore) < 0.5, "the other player stays");
		helper.runAfterDelay(5, () -> {
			cleanup(scene);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 160)
	public void guardians(GameTestHelper helper) {
		Scene scene = scene(helper, "Gardiens", new BlockPos(4, 0, 4), new BlockPos(4, 0, -4));
		cast(helper, scene, "gardiens");
		AABB area = new AABB(scene.ground()).inflate(8);
		helper.runAfterDelay(30, () -> {
			List<IronGolem> golems = scene.level().getEntitiesOfClass(IronGolem.class, area,
					g -> g.entityTags().contains("guildy_scroll_guardian"));
			helper.assertTrue(golems.size() == 2, "two guardians summoned: " + golems.size());
			for (IronGolem golem : golems) {
				helper.assertTrue(golem.isPlayerCreated() && golem.hasCustomName(), "player-made, named guardian");
				helper.assertTrue(golem.getTarget() != scene.caster() && golem.getTarget() != scene.stranger(), "never targets a player");
			}
			// A killed guardian crumbles: no iron.
			IronGolem first = golems.getFirst();
			first.hurtServer(scene.level(), scene.level().damageSources().generic(), 1000.0F);
			helper.assertTrue(first.isRemoved(), "killed guardian removed");
			helper.assertTrue(scene.level().getEntitiesOfClass(ItemEntity.class, area, i -> i.getItem().is(Items.IRON_INGOT)).isEmpty(), "no iron dropped");
			// The caster leaves for another dimension (same cleanup as a logout): the other guardian vanishes with them.
			ServerLevel nether = scene.level().getServer().getLevel(Level.NETHER);
			helper.assertTrue(nether != null, "the Nether exists");
			BlockPos away = scene.ground();
			scene.caster().teleportTo(nether, away.getX() + 0.5, 100, away.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(scene.level().getEntitiesOfClass(IronGolem.class, area, g -> g.entityTags().contains("guildy_scroll_guardian")).isEmpty(),
						"guardians gone with their caster");
				cleanup(scene);
				helper.succeed();
			});
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void blessing(GameTestHelper helper) {
		Scene scene = scene(helper, "Benediction", new BlockPos(3, 0, 1), new BlockPos(-3, 0, 0), 4);
		scene.caster().setHealth(8.0F);
		scene.villager().setHealth(scene.villager().getMaxHealth() - 8.0F);
		scene.stranger().setHealth(8.0F);
		Husk husk = scene.husks().getFirst();
		husk.setHealth(10.0F);
		cast(helper, scene, "benediction");
		float villagerBefore = scene.villager().getHealth();
		// The blessing lands on the first tick of its effect.
		helper.succeedWhen(() -> {
			helper.assertTrue(scene.caster().getHealth() > 8.0F && scene.caster().hasEffect(MobEffects.REGENERATION), "the caster is healed");
			helper.assertTrue(scene.villager().getHealth() > villagerBefore && scene.villager().hasEffect(MobEffects.REGENERATION),
					"the guild villager is healed");
			helper.assertTrue(!scene.stranger().hasEffect(MobEffects.REGENERATION) && scene.stranger().getHealth() < 12.0F,
					"a player of another guild is not");
			helper.assertTrue(husk.getHealth() == 10.0F && !husk.hasEffect(MobEffects.REGENERATION), "monsters are not");
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 140)
	public void revelation(GameTestHelper helper) {
		Scene scene = scene(helper, "Revelation", new BlockPos(11, 0, 1), new BlockPos(11, 0, -1), 12, 6);
		ItemStack left = cast(helper, scene, "revelation");
		// On cooldown: the last scroll is kept.
		InteractionResult again = UseItemCallback.EVENT.invoker().interact(scene.caster(), scene.level(), InteractionHand.MAIN_HAND);
		helper.assertTrue(again == InteractionResult.FAIL && left.getCount() == 1, "cooldown: refused, kept");
		helper.succeedWhen(() -> {
			for (Husk husk : scene.husks()) {
				helper.assertTrue(husk.hasEffect(MobEffects.GLOWING), "monster revealed");
			}
			helper.assertFalse(scene.villager().hasEffect(MobEffects.GLOWING) || scene.stranger().hasEffect(MobEffects.GLOWING),
					"the villager and the players are not marked");
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 140)
	public void earthquake(GameTestHelper helper) {
		Scene scene = scene(helper, "Seisme", new BlockPos(4, 0, 1), new BlockPos(7, 0, 1), 4, 7);
		cast(helper, scene, "seisme");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "shock waves");
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	@GameTest(environment = ENV, maxTicks = 140)
	public void arcaneRay(GameTestHelper helper) {
		// The villager and the other player stand in the beam, in front of the monster.
		Scene scene = scene(helper, "Rayon", new BlockPos(3, 0, 0), new BlockPos(5, 0, 0), 8);
		cast(helper, scene, "rayon_arcanique");
		helper.succeedWhen(() -> {
			allHurt(helper, scene, "arcane ray");
			untouched(helper, scene);
			cleanup(scene);
		});
	}

	private static int count(ServerPlayer player, String id) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (id.equals(ScrollItems.idOf(stack))) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
