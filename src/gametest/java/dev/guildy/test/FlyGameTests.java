package dev.guildy.test;

import dev.guildy.core.FlightGrants;
import dev.guildy.core.FlightOwnership.Owner;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.fly.FlyRules.Reason;
import dev.guildy.fly.GuildFly;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * /guild fly: flight granted in the own territory, taken back outside (the next fall does no damage), when a horde
 * starts and when the guild is disbanded; creative and foreign flights are never touched; the Bottes de Zéphyr double
 * jump and guild flight share mayfly without fighting.
 */
public class FlyGameTests {

	/** A founded guild (creative leader) and a connected survival member standing at the heart, able to take damage. */
	private record Scene(ServerLevel level, BlockPos ground, Guild guild, ServerPlayer member) {
		/** A spot two chunks east of the heart chunk: outside the 3 x 3 founding claim, still force-loaded. */
		BlockPos outside() {
			return ground.offset(40, 0, 0);
		}
	}

	private static Scene scene(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, prefix);
		ServerPlayer member = TestSupport.survivor(helper, ground.east(2), TestSupport.uniqueName("fly"));
		GuildManager.addMember(guild, member.getUUID(), member.getName().getString());
		for (int i = 0; i < 200 && !member.connection.hasClientLoaded(); i++) {
			member.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(member.connection.hasClientLoaded(), "mock player can be hurt");
		member.setOnGround(true);
		return new Scene(level, ground, guild, member);
	}

	private static void command(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void teleport(ServerPlayer player, BlockPos ground, int above, boolean onGround) {
		player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY() + 1 + above, ground.getZ() + 0.5, Set.of(), 0, 0, true);
		player.setOnGround(onGround);
	}

	private static void startFlying(ServerPlayer player) {
		Abilities abilities = player.getAbilities();
		abilities.flying = abilities.mayfly; // what the server accepts from a client double tap
		player.setOnGround(false);
	}

	@GameTest(maxTicks = 400)
	public void flyInTerritoryAndFallSafelyOutside(GameTestHelper helper) {
		Scene scene = scene(helper, "Fly");
		ServerPlayer member = scene.member();
		Abilities abilities = member.getAbilities();
		helper.assertFalse(abilities.mayfly, "survival member: no flight before /guild fly");

		command(member, "guild fly");
		helper.assertTrue(GuildFly.preference(member), "preference switched on");
		helper.assertTrue(abilities.mayfly && GuildFly.granted(member), "flight granted right away in the territory");
		helper.assertTrue(member.entityTags().contains(Owner.GUILD_FLY.tag), "grant marked for the crash cleanup");
		startFlying(member);
		helper.assertTrue(abilities.flying, "flying");

		// Fly away: 10 blocks above the ground, two chunks outside the claim.
		teleport(member, scene.outside(), 10, false);
		float health = member.getHealth();
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(abilities.mayfly, "waiting for the flight to be taken back outside"))
				.thenExecute(() -> {
					helper.assertFalse(abilities.flying, "flying stopped");
					helper.assertFalse(GuildFly.granted(member), "grant released");
					helper.assertFalse(member.entityTags().contains(Owner.GUILD_FLY.tag), "grant tag removed");
					helper.assertTrue(GuildFly.preference(member), "the preference stays on");
					helper.assertTrue(GuildFly.fallProtected(member), "taken back in the air: fall guard armed");
					helper.assertTrue(GuildFly.status(member) == Reason.OUTSIDE, "status: outside");

					// The landing: a lethal fall does nothing, once.
					member.causeFallDamage(30.0, 1.0F, scene.level().damageSources().fall());
					helper.assertTrue(member.getHealth() == health && member.isAlive(), "protected fall: no damage (health " + member.getHealth() + ")");
					helper.assertFalse(GuildFly.fallProtected(member), "the guard is used up");
					member.causeFallDamage(8.0, 1.0F, scene.level().damageSources().fall());
					helper.assertTrue(member.getHealth() < health, "a later fall hurts again");

					// Back home on the ground: flight is available again.
					teleport(member, scene.ground(), 0, true);
				})
				.thenWaitUntil(() -> helper.assertTrue(abilities.mayfly && GuildFly.granted(member), "waiting for flight back in the territory"))
				.thenExecute(() -> {
					command(member, "guild fly off");
					helper.assertFalse(GuildFly.preference(member), "preference off");
					helper.assertFalse(abilities.mayfly || GuildFly.granted(member), "switched off: flight taken back");
					helper.assertFalse(GuildFly.fallProtected(member), "on the ground: no fall guard needed");

					command(member, "guild fly on");
					helper.assertTrue(abilities.mayfly, "on again");
					GuildManager.disband(scene.guild());
					helper.assertFalse(abilities.mayfly || GuildFly.granted(member), "disbanded guild: flight taken back");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void hordeTakesTheFlightBack(GameTestHelper helper) {
		Scene scene = scene(helper, "FlyHorde");
		ServerPlayer member = scene.member();
		Abilities abilities = member.getAbilities();
		command(member, "guild fly");
		helper.assertTrue(abilities.mayfly, "flight granted");
		startFlying(member);

		TestSupport.console(scene.level(), "guild admin wave start " + scene.guild().name);
		helper.assertTrue(dev.guildy.wave.WaveManager.isActive(scene.guild()), "test wave running");
		// Taken back at once (WAVE_STARTED), not at the next periodic check.
		helper.assertFalse(abilities.mayfly || abilities.flying, "the horde arrives: no more flight");
		helper.assertTrue(GuildFly.fallProtected(member), "was flying: fall guard armed");
		helper.assertTrue(GuildFly.status(member) == Reason.HORDE, "status: horde");
		helper.assertTrue(TestSupport.chat(member).stream().anyMatch(line -> line.contains("horde")), "told in the chat");

		helper.startSequence()
				.thenIdle(10)
				.thenExecute(() -> {
					helper.assertFalse(abilities.mayfly, "never granted again while the horde runs");
					TestSupport.console(scene.level(), "guild admin wave stop " + scene.guild().name);
					teleport(member, scene.ground(), 0, true);
				})
				.thenWaitUntil(() -> helper.assertTrue(abilities.mayfly && GuildFly.granted(member), "waiting for flight after the horde"))
				.thenExecute(() -> {
					GuildManager.disband(scene.guild());
					helper.assertFalse(abilities.mayfly, "flight taken back with the guild");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void creativeAndForeignFlightsAreLeftAlone(GameTestHelper helper) {
		Scene scene = scene(helper, "FlyFree");
		// A creative member: the game mode owns the flight.
		ServerPlayer builder = TestSupport.player(helper, scene.ground().west(2));
		GuildManager.addMember(scene.guild(), builder.getUUID(), builder.getName().getString());
		command(builder, "guild fly");
		helper.assertTrue(GuildFly.preference(builder), "the preference may be switched on in creative");
		helper.assertFalse(GuildFly.granted(builder), "creative flight is never managed");
		builder.getAbilities().flying = true;

		// A survival member whose flight comes from elsewhere (an operator, another mod).
		ServerPlayer member = scene.member();
		member.getAbilities().mayfly = true;
		member.onUpdateAbilities();
		command(member, "guild fly");
		helper.assertFalse(GuildFly.granted(member), "a foreign flight is not taken over");

		teleport(builder, scene.outside(), 6, false);
		builder.getAbilities().flying = true;
		teleport(member, scene.outside(), 6, false);
		helper.startSequence()
				.thenIdle(12)
				.thenExecute(() -> {
					helper.assertTrue(builder.getAbilities().mayfly && builder.getAbilities().flying, "creative flight untouched outside");
					helper.assertTrue(member.getAbilities().mayfly, "foreign flight never revoked");
					helper.assertFalse(GuildFly.fallProtected(builder) || GuildFly.fallProtected(member), "nothing taken back, no guard");
					member.getAbilities().mayfly = false;
					member.onUpdateAbilities();
					command(builder, "guild fly off");
					command(member, "guild fly off");
					GuildManager.disband(scene.guild());
					helper.assertTrue(builder.getAbilities().mayfly, "creative flight untouched by the disband");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void doubleJumpAndGuildFlyShareTheFlight(GameTestHelper helper) {
		Scene scene = scene(helper, "FlyBoots");
		ServerPlayer member = scene.member();
		Abilities abilities = member.getAbilities();
		ItemStack boots = UniqueDrops.create("bottes_de_zephyr");
		helper.assertFalse(boots.isEmpty(), "Bottes de Zéphyr created");
		member.setItemSlot(EquipmentSlot.FEET, boots);

		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(FlightGrants.holders(member).equals(EnumSet.of(Owner.DOUBLE_JUMP)) && abilities.mayfly,
						"waiting for the double jump to grant mayfly on the ground"))
				.thenExecute(() -> {
					command(member, "guild fly");
					helper.assertTrue(GuildFly.granted(member), "guild flight joins the double jump's grant");
				})
				.thenWaitUntil(() -> helper.assertTrue(FlightGrants.holders(member).equals(EnumSet.of(Owner.GUILD_FLY)),
						"waiting for the double jump to step aside"))
				.thenExecute(() -> startFlying(member))
				.thenIdle(4)
				.thenExecute(() -> {
					helper.assertTrue(abilities.flying && abilities.mayfly, "real flight: not turned into a boost, not revoked");
					teleport(member, scene.outside(), 8, false);
					abilities.flying = true;
				})
				.thenWaitUntil(() -> helper.assertFalse(GuildFly.granted(member), "waiting for the flight to end outside"))
				.thenExecute(() -> {
					helper.assertFalse(abilities.mayfly || abilities.flying, "nobody holds mayfly in the air (no boots grant mid-air)");
					helper.assertTrue(GuildFly.fallProtected(member), "fall guard armed");
					member.setOnGround(true);
				})
				.thenWaitUntil(() -> helper.assertTrue(FlightGrants.holders(member).equals(EnumSet.of(Owner.DOUBLE_JUMP)) && abilities.mayfly,
						"waiting for the double jump to take over again after the landing"))
				.thenExecute(() -> startFlying(member))
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertFalse(abilities.flying || abilities.mayfly, "outside the territory a double tap is a boost again");
					teleport(member, scene.ground(), 0, true);
				})
				.thenWaitUntil(() -> helper.assertTrue(FlightGrants.holders(member).equals(EnumSet.of(Owner.GUILD_FLY)) && abilities.mayfly,
						"waiting for guild flight back home (double jump aside)"))
				.thenExecute(() -> member.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY))
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(abilities.mayfly && GuildFly.granted(member), "boots removed: guild flight keeps mayfly");
					GuildManager.disband(scene.guild());
					helper.assertFalse(abilities.mayfly, "last owner gone: mayfly taken back");
					helper.assertTrue(FlightGrants.holders(member).isEmpty(), "no holder left");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void leftoverGrantIsTakenBackAtLogin(GameTestHelper helper) {
		Scene scene = scene(helper, "FlyCrash");
		ServerPlayer member = scene.member();
		command(member, "guild fly");
		helper.assertTrue(member.getAbilities().mayfly, "granted");
		// A crash: the grant was saved with the player (tag + mayfly) while the server forgot it.
		FlightGrants.forget(member, Owner.GUILD_FLY);
		member.addTag(Owner.GUILD_FLY.tag);
		FlightGrants.onJoin(member);
		helper.assertFalse(member.getAbilities().mayfly, "leftover grant taken back at login");
		helper.assertFalse(member.entityTags().contains(Owner.GUILD_FLY.tag), "tag cleaned");
		helper.assertTrue(GuildFly.preference(member), "the preference itself survives");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(member.getAbilities().mayfly && GuildFly.granted(member),
						"waiting for the next check to grant it again (still in the territory)"))
				.thenExecute(() -> GuildManager.disband(scene.guild()))
				.thenSucceed();
	}
}
