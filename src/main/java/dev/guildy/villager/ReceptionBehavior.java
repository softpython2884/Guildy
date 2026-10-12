package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * "Bureau d'accueil" (structure id "reception", defined by the structure module): its managed sign shows the villager
 * count and the pending candidate, a click opens the villager menu, and building it brings a first candidate.
 */
final class ReceptionBehavior implements StructureBehavior {
	@Override
	public void onBuilt(ServerLevel level, Guild guild, BuiltStructure structure, ServerPlayer builder) {
		Safe.run("villager.reception.built", () -> {
			VillagerManager.scheduleFirstArrival(guild);
			Msg.send(builder, "villager.reception.built", VillagerModule.config().arrivals.firstArrivalDelaySeconds);
		});
	}

	@Override
	public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
		Safe.run("villager.reception.use", () -> {
			if (!guild.isMember(player.getUUID())) {
				Msg.send(player, "villager.error.not_your_reception", guild.name);
				return;
			}
			VillagerGui.openList(player, guild);
		});
	}

	@Override
	public @Nullable List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
		return Safe.get("villager.reception.sign", () -> {
			List<VillagerManager.Candidate> candidates = VillagerManager.candidates(guild);
			Component waiting = candidates.isEmpty() ? Component.empty()
					: candidates.size() == 1 ? Lang.get("villager.sign.candidate", candidates.getFirst().name())
					: Lang.get("villager.sign.candidates", candidates.size());
			return List.of(
					Lang.get("villager.sign.title"),
					Lang.get("villager.sign.count", guild.villagers.members.size(), VillagerManager.maxVillagers(guild)),
					waiting,
					Lang.get("villager.sign.click"));
		}, null);
	}
}
