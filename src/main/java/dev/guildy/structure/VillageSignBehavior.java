package dev.guildy.structure;

import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * "Panneau du village": a one-block structure whose sign shows the guild name and level; right-click opens the
 * constructions menu, sneak + right-click the main guild menu (a shortcut for /guild).
 */
final class VillageSignBehavior implements StructureBehavior {
	static final String ID = "village_sign";

	@Override
	public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
		if (player.isShiftKeyDown()) {
			Hooks.openMainMenu.accept(player);
		} else {
			Hooks.openStructures.accept(player, guild);
		}
	}

	@Override
	public List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
		Component status = Hooks.isWaveActive.test(guild)
				? Lang.get("structure.village_sign.wave")
				: Lang.get("structure.village_sign.online", guild.onlineMembers().size());
		return List.of(
				Lang.get("structure.village_sign.line1", guild.name),
				Lang.get("structure.village_sign.line2", guild.level),
				status,
				Lang.get("structure.village_sign.hint"));
	}
}
