package dev.guildy.scroll.mixin;

import dev.guildy.core.Safe;
import dev.guildy.scroll.Grimoires;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak + swap hands key (F by default) with a grimoire in a hand opens its pages menu instead of swapping the hands (the
 * vanilla client does not predict the swap, so nothing needs to be sent back). The packet is first seen on the network
 * thread, then handled again on the server thread: only that second pass is considered.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerGrimoireMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void guildy$grimoireMenu(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			return;
		}
		try {
			MinecraftServer server = this.player.level().getServer();
			if (server != null && server.isSameThread() && Grimoires.onSwapHands(this.player)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			Safe.log("mixin.grimoire.swap", t);
		}
	}
}
