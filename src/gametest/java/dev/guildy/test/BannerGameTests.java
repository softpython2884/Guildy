package dev.guildy.test;

import dev.guildy.command.GuildActions;
import dev.guildy.core.GuildyItems;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Guild flag identity: a founding flag woven at the loom keeps working and its design becomes the guild banner,
 * adopting a banner held in hand repaints the heart, and the heart shows up on held maps with the guild name.
 */
public class BannerGameTests {

	@GameTest(maxTicks = 200)
	public void wovenFlagAdoptedBannerAndMapMarker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = GuildManager.create(TestSupport.uniqueName("Flag"), leader);

		// Weave the founding flag like the loom does (copy + extra pattern layer; custom data is kept).
		Holder<BannerPattern> cross = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.CROSS);
		ItemStack flag = GuildyItems.foundingFlag(guild);
		flag.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(cross, DyeColor.RED).build());
		helper.assertTrue(GuildyItems.is(flag, GuildyItems.KIND_FOUNDING_FLAG), "woven flag is still a founding flag");
		TestSupport.useOnTop(leader, level, ground, flag);
		helper.assertTrue(guild.isFounded(), "woven founding flag planted");
		BlockPos heart = guild.heart;
		helper.assertTrue(guild.banner.layers.size() == 1 && "minecraft:cross".equals(guild.banner.layers.getFirst().pattern),
				"guild banner adopted the woven design");
		BannerBlockEntity planted = (BannerBlockEntity) level.getBlockEntity(heart);
		helper.assertTrue(planted.getPatterns().layers().size() == 1, "heart banner shows the woven pattern");
		helper.assertTrue(planted.getCustomName() != null && planted.getCustomName().getString().equals(guild.name),
				"heart banner carries the guild name (map label)");

		// Adopt a red banner held in hand: guild design + heart repainted.
		ItemStack red = new ItemStack(Items.BANNER.pick(DyeColor.RED));
		leader.setItemInHand(InteractionHand.MAIN_HAND, red);
		helper.assertTrue(GuildActions.adoptBanner(leader), "adopt the banner in hand");
		helper.assertTrue(guild.banner.baseColor() == DyeColor.RED && guild.banner.layers.isEmpty(), "guild banner is now plain red");
		helper.assertTrue(level.getBlockState(heart).getBlock() instanceof AbstractBannerBlock banner && banner.getColor() == DyeColor.RED,
				"heart banner repainted red");

		// A map held in hand gets the heart marker (colored banner + guild name). Scale 1: test hearts sit on a
		// 128-block map cell boundary, i.e. on the very edge of scale-0 maps (outside the marker area, like vanilla).
		ItemStack map = MapItem.create(level, heart.getX(), heart.getZ(), (byte) 1, true, false);
		leader.setItemInHand(InteractionHand.MAIN_HAND, map);
		MapItemSavedData data = MapItem.getSavedData(map, level);
		helper.assertTrue(data != null, "map data created");
		helper.succeedWhen(() -> {
			boolean marked = data.getBanners().stream().anyMatch(b -> b.pos().equals(heart)
					&& b.color() == DyeColor.RED && b.name().map(n -> n.getString().equals(guild.name)).orElse(false));
			helper.assertTrue(marked, "waiting for the heart marker on the held map");
			GuildManager.disband(guild);
		});
	}
}
