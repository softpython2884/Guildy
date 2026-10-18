package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.ruin.GuildRuinData.Research;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * "Atelier du cartographe" (structure id {@value Cartography#STRUCTURE_ID}): its sign shows the research, a click opens
 * the atelier menu (research, collection, known ruins).
 */
final class CartographerBehavior implements StructureBehavior {
	@Override
	public void onBuilt(ServerLevel level, Guild guild, BuiltStructure structure, ServerPlayer builder) {
		Safe.run("ruin.atelier.built", () -> {
			if (Cartography.cartographers(guild) == 0) {
				Msg.send(builder, "ruin.atelier.built.hint");
			}
		});
	}

	@Override
	public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
		Safe.run("ruin.atelier.use", () -> AtelierMenu.open(player, guild));
	}

	@Override
	public @Nullable List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
		return Safe.get("ruin.atelier.sign", () -> {
			Research research = Cartography.research(guild);
			Component status;
			if (research == null) {
				status = Lang.get("ruin.sign.idle");
			} else if (research.status == Research.Status.RUNNING) {
				status = Lang.get("ruin.sign.progress", RuinRules.percent(research.progress, research.required));
			} else if (research.status == Research.Status.READY) {
				status = Lang.get(research.legend ? "ruin.sign.ready.legend" : research.nether ? "ruin.sign.ready.nether" : "ruin.sign.ready");
			} else {
				status = Lang.get("ruin.sign.failed");
			}
			return List.of(
					Lang.get("ruin.sign.title"),
					Lang.get("ruin.sign.cartographers", Cartography.cartographers(guild)),
					status,
					Lang.get("ruin.sign.click"));
		}, null);
	}
}
