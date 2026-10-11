package dev.guildy.test;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.quest.HordeMobs;
import dev.guildy.quest.QuestConfig;
import dev.guildy.quest.QuestInstance;
import dev.guildy.quest.QuestManager;
import dev.guildy.quest.QuestMenus;
import dev.guildy.quest.QuestPart;
import dev.guildy.quest.QuestTarget;
import dev.guildy.quest.QuestTemplate;
import dev.guildy.quest.QuestType;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quest types through the real game code paths: furnace and crafting result slots (mixins), horde elites / bosses killed
 * by a member, the periodic biome check, assortment deliveries, legendary contracts (admin command, golden sign and menus,
 * unique weapon hook or gold instead), and the validity of every default template in the 26.3 registries.
 */
public class QuestGameTests {

	/** Every default template resolves: blocks, items, entities, tags, biomes, icons, rewards, assortment items. */
	@GameTest(maxTicks = 20)
	public void defaultQuestTemplatesAreValid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QuestConfig config = new QuestConfig();
		config.validate();
		config.checkBiomes(level.registryAccess());
		List<String> unusable = new ArrayList<>();
		for (QuestTemplate template : config.templates) {
			if (!config.usable().containsKey(template.id)) {
				unusable.add(template.id);
			}
		}
		helper.assertTrue(unusable.isEmpty(), "default quests ignored by the validation: " + unusable);
		Map<String, QuestTemplate> defaults = new java.util.HashMap<>();
		for (QuestTemplate template : QuestConfig.defaults()) {
			defaults.put(template.id, template);
		}
		for (QuestTemplate template : config.templates) {
			helper.assertTrue(template.icon.equals(defaults.get(template.id).icon), template.id + ": unknown icon " + defaults.get(template.id).icon);
			for (QuestTemplate.RewardItem reward : template.rewardItems) {
				helper.assertTrue(ItemUtil.item(reward.item) != null, template.id + ": unknown reward " + reward.item);
			}
			if (template.collection != null) {
				for (QuestTemplate.Part part : template.collection.items) {
					helper.assertTrue(QuestTarget.parse(QuestType.TargetKind.ITEM, part.item) != null, template.id + ": unknown item " + part.item);
				}
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void eventDrivenQuestTypes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Quest");
		ServerPlayer member = TestSupport.survivor(helper, ground.east(2));
		GuildManager.addMember(guild, member.getUUID(), member.getName().getString());

		// SMELT through the real furnace result slot (shift-click): never in creative, counted for a survival member.
		QuestInstance smelt = addQuest(guild, 0, QuestType.SMELT, "minecraft:iron_ingot", 5);
		BlockPos furnacePos = ground.offset(3, 1, 0);
		level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
		FurnaceBlockEntity furnace = (FurnaceBlockEntity) level.getBlockEntity(furnacePos);
		helper.assertTrue(furnace != null, "furnace placed");
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
		leader.openMenu(furnace);
		leader.containerMenu.clicked(2, 0, ContainerInput.QUICK_MOVE, leader);
		helper.assertTrue(furnace.getItem(2).isEmpty(), "the creative player took the ingots out");
		helper.assertTrue(smelt.progress == 0, "creative players never count, progress " + smelt.progress);
		leader.closeContainer();
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
		member.openMenu(furnace);
		member.containerMenu.clicked(2, 0, ContainerInput.QUICK_MOVE, member);
		helper.assertTrue(ItemUtil.count(member, Items.IRON_INGOT) == 8, "the member took the 8 ingots");
		helper.assertTrue(smelt.isCompleted(), "SMELT completed (5 of 8), progress " + smelt.progress);
		helper.assertTrue(smelt.contributions.getOrDefault(member.getName().getString(), 0) == 5, "contribution " + smelt.contributions);
		member.closeContainer();

		// CRAFT through the inventory crafting grid (shift-click on the result).
		QuestInstance craft = addQuest(guild, 1, QuestType.CRAFT, "#minecraft:planks", 8);
		member.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
		helper.assertTrue(member.inventoryMenu.getSlot(0).getItem().is(Items.OAK_PLANKS), "planks in the result slot");
		member.inventoryMenu.clicked(0, 0, ContainerInput.QUICK_MOVE, member);
		helper.assertTrue(craft.progress == 4 && craft.isActive(), "4 planks crafted, progress " + craft.progress);
		helper.assertTrue(ItemUtil.count(member, Items.OAK_PLANKS) == 4, "planks received");

		// ELITE / BOSS: horde mobs recognised by the wave markers; plain horde mobs do not count.
		QuestInstance elite = addQuest(guild, 2, QuestType.ELITE, "", 1);
		QuestInstance boss = addQuest(guild, 3, QuestType.BOSS, "", 2);
		killHordeMob(helper, level, member, ground, HordeMobs.Rank.NORMAL);
		helper.assertTrue(elite.progress == 0 && boss.progress == 0, "a plain horde mob is neither an elite nor a boss");
		killHordeMob(helper, level, member, ground, HordeMobs.Rank.ELITE);
		helper.assertTrue(elite.isCompleted() && boss.progress == 0, "elite killed");
		killHordeMob(helper, level, member, ground, HordeMobs.Rank.BOSS);
		helper.assertTrue(boss.progress == 1 && boss.isActive(), "boss killed, 1/2");
		killHordeMob(helper, level, leader, ground, HordeMobs.Rank.BOSS);
		helper.assertTrue(boss.progress == 1, "creative kills never count");

		// BIOME: the biome under the member's feet, found by the periodic quest check (every 5 seconds per guild).
		Holder<Biome> here = level.getBiome(member.blockPosition());
		String biomeId = here.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:plains");
		QuestInstance biome = addQuest(guild, 4, QuestType.BIOME, biomeId, 1);
		QuestInstance elsewhere = addQuest(guild, 5, QuestType.BIOME, "minecraft:deep_dark".equals(biomeId) ? "minecraft:desert" : "minecraft:deep_dark", 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(biome.isCompleted(), "waiting for the biome check (" + biomeId + ")");
			helper.assertTrue(elsewhere.isActive() && elsewhere.progress == 0, "another biome does not count");
			GuildManager.disband(guild);
		});
	}

	@GameTest(maxTicks = 100)
	public void assortmentAndLegendaryContract(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Contrat");
		ServerPlayer member = TestSupport.survivor(helper, ground.east(2));
		GuildManager.addMember(guild, member.getUUID(), member.getName().getString());

		// COLLECTION: every missing item delivered at once, then one item alone; per-item progress.
		QuestInstance pack = addQuest(guild, 0, QuestType.COLLECTION, "", 6);
		pack.parts = new ArrayList<>(List.of(new QuestPart("minecraft:bread", "", 4), new QuestPart("#minecraft:wool", "Laine", 2)));
		member.getInventory().add(new ItemStack(Items.BREAD, 6));
		member.getInventory().add(new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 1));
		helper.assertTrue(QuestManager.deliverable(member, pack, 0) == 6 && QuestManager.deliverable(member, pack, 1) == 1, "deliverable counts");
		helper.assertTrue(QuestManager.deliver(leader, guild, pack) != null, "creative players cannot deliver");
		helper.assertTrue(QuestManager.deliver(member, guild, pack) == null, "assortment delivered");
		helper.assertTrue(pack.parts.get(0).isDone() && pack.parts.get(1).progress == 1, "bread done, wool 1/2");
		helper.assertTrue(pack.progress == 5 && pack.isActive(), "total 5/6, progress " + pack.progress);
		helper.assertTrue(ItemUtil.count(member, Items.BREAD) == 2, "only the missing bread was taken");
		helper.assertTrue(QuestManager.deliver(member, guild, pack, 1) != null, "no wool left");
		member.getInventory().add(new ItemStack(Items.WOOL.pick(DyeColor.RED), 3));
		helper.assertTrue(QuestManager.deliver(member, guild, pack, 1) == null && pack.isCompleted(), "assortment complete");
		helper.assertTrue(ItemUtil.count(member, Items.WOOL.pick(DyeColor.RED)) == 2, "one red wool taken");

		// Signs and menus of every type (4 lines, no error).
		StructureBehavior board = StructureBehavior.get(QuestManager.BOARD);
		helper.assertTrue(board != null, "quest board behaviour registered");
		for (QuestType type : QuestType.values()) {
			QuestInstance quest = addQuest(guild, 1, type, sampleTarget(type), 3);
			if (type == QuestType.COLLECTION) {
				quest.parts = new ArrayList<>(List.of(new QuestPart("minecraft:apple", "", 2), new QuestPart("minecraft:carrot", "", 1)));
			}
			List<Component> lines = board.signText(guild, new BuiltStructure(), 1);
			helper.assertTrue(lines != null && lines.size() == 4 && !lines.get(0).getString().isBlank(), type + ": sign " + lines);
			member.closeContainer();
			QuestMenus.openDetail(member, guild, quest.id);
			helper.assertTrue(member.containerMenu != member.inventoryMenu, type + ": detail menu open");
			guild.quests.active.remove(quest);
		}
		member.closeContainer();
		QuestMenus.openDetail(member, guild, pack.id);
		helper.assertTrue(member.containerMenu.getSlot(12).getItem().is(Items.BREAD), "first item of the assortment");
		helper.assertTrue(member.containerMenu.getSlot(14).getItem().is(TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("wool"))),
				"second item of the assortment (a wool of the tag)");
		guild.quests.active.remove(pack);

		// Legendary contract forced by an admin on board slot 3: golden sign, golden frame, unique weapon at the claim.
		GuildManager.setLevel(guild, 3);
		TestSupport.console(level, "guild admin quest legendary " + guild.name + " 3");
		QuestInstance legendary = guild.quests.bySlot(2);
		helper.assertTrue(legendary != null && legendary.legendary, "legendary contract on slot 3");
		helper.assertTrue(guild.quests.hasLegendary() && guild.quests.lastLegendaryAt > 0, "legendary contract remembered");
		QuestTemplate template = QuestManager.config().usable().get(legendary.template);
		helper.assertTrue(template != null && template.legendaryAllowed(), "drawn among the legendary candidates: " + legendary.template);
		List<Component> sign = board.signText(guild, new BuiltStructure(), 2);
		helper.assertTrue(sign.get(3).getString().contains("✦"), "legendary reward line: " + sign.get(3).getString());
		member.closeContainer();
		QuestMenus.openList(member, guild);
		helper.assertTrue(member.containerMenu.getSlot(16).getItem().is(Items.STAINED_GLASS_PANE.pick(DyeColor.YELLOW)), "golden frame");
		helper.assertTrue(Boolean.TRUE.equals(member.containerMenu.getSlot(15).getItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"legendary contract glows");
		member.closeContainer();

		UniqueDrops.Roller previous = UniqueDrops.roller;
		List<String> sources = new ArrayList<>();
		try {
			UniqueDrops.roller = (source, random) -> {
				sources.add(source);
				return new ItemStack(Items.NETHERITE_SWORD);
			};
			helper.assertTrue(QuestManager.forceComplete(guild, 2), "admin completion");
			legendary.rewardMap = false; // (a map without a valid spot would pay gold instead: keep the totals exact)
			long before = guild.treasury;
			helper.assertTrue(QuestManager.claim(member, guild, legendary) == null, "legendary reward claimed");
			helper.assertTrue(sources.equals(List.of(QuestManager.UNIQUE_SOURCE_LEGENDARY)), "unique weapon rolled once: " + sources);
			helper.assertTrue(ItemUtil.count(member, Items.NETHERITE_SWORD) == 1, "unique weapon received");
			helper.assertTrue(guild.treasury == before + QuestManager.rewardGold(guild, legendary), "gold paid, no fallback");
			helper.assertTrue(guild.quests.legendaryCompleted == 1, "legendary contracts counted");

			// No unique weapon available: gold instead.
			UniqueDrops.roller = (source, random) -> ItemStack.EMPTY;
			TestSupport.console(level, "guild admin quest legendary " + guild.name + " 4");
			QuestInstance second = guild.quests.bySlot(3);
			helper.assertTrue(second != null && second.legendary, "second legendary contract");
			helper.assertTrue(QuestManager.forceComplete(guild, 3), "admin completion");
			second.rewardMap = false;
			long before2 = guild.treasury;
			helper.assertTrue(QuestManager.claim(member, guild, second) == null, "second reward claimed");
			long expected = before2 + QuestManager.rewardGold(guild, second) + QuestManager.config().legendary.fallbackGold;
			helper.assertTrue(guild.treasury == expected, "fallback gold: " + guild.treasury + " != " + expected);
		} finally {
			UniqueDrops.roller = previous;
		}
		GuildManager.disband(guild);
		helper.succeed();
	}

	private static String sampleTarget(QuestType type) {
		return switch (type) {
			case GATHER -> "minecraft:stone";
			case KILL -> "minecraft:zombie";
			case DELIVER -> "minecraft:bread";
			case CRAFT -> "minecraft:torch";
			case SMELT -> "#minecraft:meat";
			case BREED -> "minecraft:cow";
			case TRADE -> "minecraft:emerald";
			case BIOME -> "#minecraft:is_jungle";
			default -> "";
		};
	}

	/** A quest added directly to the guild (no board needed for progress). */
	private static QuestInstance addQuest(Guild guild, int slot, QuestType type, String target, int amount) {
		QuestInstance quest = new QuestInstance();
		quest.id = UUID.randomUUID().toString();
		quest.template = "test_" + type.key();
		quest.slot = slot;
		quest.type = type;
		quest.name = "Test " + type.key();
		quest.target = target;
		quest.amount = amount;
		quest.rewardGold = 5;
		quest.rewardXp = 10;
		quest.createdAt = QuestManager.now();
		quest.expiresAt = QuestManager.now() + 24000;
		guild.quests.active.add(quest);
		return quest;
	}

	/** Spawns a zombie carrying the wave module's markers for {@code rank} and lets {@code killer} kill it. */
	private static void killHordeMob(GameTestHelper helper, ServerLevel level, ServerPlayer killer, BlockPos ground, HordeMobs.Rank rank) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(zombie != null, "zombie created");
		zombie.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() - 1.5, 0.0F, 0.0F);
		level.addFreshEntity(zombie);
		// Markers added after spawning, like a live horde mob (a tagged mob loaded without its horde would be removed).
		zombie.addTag(HordeMobs.WAVE_TAG);
		if (rank == HordeMobs.Rank.ELITE) {
			zombie.setCustomName(Lang.get("wave.elite.prefix").append(Component.literal("Zombie")));
			zombie.setGlowingTag(true);
		} else if (rank == HordeMobs.Rank.BOSS) {
			zombie.setCustomName(Component.literal("Seigneur des morts"));
			zombie.setCustomNameVisible(true);
			zombie.setGlowingTag(true);
		}
		helper.assertTrue(HordeMobs.rank(zombie) == rank, "rank " + HordeMobs.rank(zombie) + " != " + rank);
		zombie.hurtServer(level, killer.damageSources().playerAttack(killer), 10000.0F);
		helper.assertTrue(zombie.isDeadOrDying(), "horde mob killed");
	}
}
