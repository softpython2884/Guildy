package dev.guildy.gui;

import dev.guildy.claim.ClaimService;
import dev.guildy.command.GuildActions.Denial;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.data.GuildRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Territory overview and claim actions. Rules are enforced by {@link ClaimService}; the checks here only explain
 * up-front why a button is unavailable. Non-null results of ClaimService are lang keys sent to the player.
 */
final class TerritoryMenu extends GuildGui {
	TerritoryMenu(ServerPlayer player, Guild guild) {
		super(player, 5, guild);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("menu.territory.title");
	}

	private long currentChunk() {
		return ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ());
	}

	private boolean inClaimLevel() {
		return GuildyServer.isClaimLevel(player.level());
	}

	private int extensionFlags() {
		int count = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (GuildyItems.is(stack, GuildyItems.KIND_EXTENSION_FLAG)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static @Nullable Denial permission(Guild guild, ServerPlayer player, GuildPermission permission) {
		return guild.can(player.getUUID(), permission) ? null : new Denial("error.no_permission");
	}

	// ---------------------------------------------------------------- checks (re-evaluated at click time)

	private @Nullable Denial claimDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		Denial denial = permission(guild, player, GuildPermission.CLAIM);
		if (denial != null) {
			return denial;
		}
		if (!guild.isFounded()) {
			return new Denial("menu.territory.not_founded");
		}
		if (!inClaimLevel()) {
			return new Denial("menu.territory.wrong_dimension");
		}
		Guild owner = GuildManager.atChunk(currentChunk());
		if (owner == guild) {
			return new Denial("menu.territory.already_own");
		}
		if (owner != null) {
			return new Denial("menu.territory.other_owner", owner.name);
		}
		if (guild.claimCount() >= guild.maxChunks()) {
			return new Denial("menu.territory.max_chunks", guild.maxChunks());
		}
		if (extensionFlags() <= 0) {
			return new Denial("menu.territory.no_flag");
		}
		return null;
	}

	private @Nullable Denial unclaimDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		Denial denial = permission(guild, player, GuildPermission.CLAIM);
		if (denial != null) {
			return denial;
		}
		if (!inClaimLevel() || !guild.owns(currentChunk())) {
			return new Denial("menu.territory.not_own");
		}
		if (guild.isFounded() && guild.heartChunk() == currentChunk()) {
			return new Denial("menu.territory.heart_chunk");
		}
		return null;
	}

	private @Nullable Denial buyFlagDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		Denial denial = permission(guild, player, GuildPermission.TREASURY);
		if (denial != null) {
			return denial;
		}
		int price = ClaimService.extensionFlagPrice(guild);
		return guild.treasury < price ? new Denial("error.not_enough_treasury", price) : null;
	}

	private @Nullable Denial foundingFlagDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (guild.isFounded()) {
			return new Denial("menu.territory.already_founded");
		}
		return permission(guild, player, GuildPermission.CLAIM);
	}

	private @Nullable Denial setHomeDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		Denial denial = permission(guild, player, GuildPermission.SET_HOME);
		if (denial != null) {
			return denial;
		}
		return guild.isFounded() ? null : new Denial("menu.territory.not_founded");
	}

	private @Nullable Denial homeDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		return guild.home == null ? new Denial("menu.territory.no_home") : null;
	}

	private @Nullable Denial abandonDenial() {
		Guild guild = guild();
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (guild.role(player.getUUID()) != GuildRole.LEADER) {
			return new Denial("guild.leader_only");
		}
		return guild.isFounded() || guild.claimCount() > 0 ? null : new Denial("menu.territory.not_founded");
	}

	/** Sends the ClaimService error (lang key) if any. */
	private boolean result(@Nullable String error) {
		if (error != null) {
			Msg.send(player, error);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		setSlot(4, icon(Items.GRASS_BLOCK, Lang.get("menu.territory.info.name", guild.name), infoLore(guild)));
		setSlot(10, icon(Items.COMPASS, Lang.get("menu.territory.chunk.name", ChunkKey.x(currentChunk()), ChunkKey.z(currentChunk())), chunkLore(guild)));

		action(12, icon(Items.BANNER.pick(DyeColor.LIME), "menu.territory.claim.name",
						GuiUtil.lines("menu.territory.claim.lore", extensionFlags())),
				this::claimDenial, () -> result(ClaimService.claimWithFlagFromInventory(player, guild, currentChunk())));
		action(14, icon(Items.BANNER.pick(DyeColor.RED), "menu.territory.unclaim.name",
						GuiUtil.lines("menu.territory.unclaim.lore")),
				this::unclaimDenial, () -> result(ClaimService.unclaim(player, guild, currentChunk())));
		button(16, icon(Items.SPYGLASS, "menu.territory.borders.name",
						GuiUtil.lines("menu.territory.borders.lore", GuildyServer.config().territory.borderDisplaySeconds)),
				null, () -> {
					close();
					ClaimService.showBorders(player, guild);
				});

		// Icons are plain banners in the guild colors (never the real flag items).
		int price = ClaimService.extensionFlagPrice(guild);
		action(20, icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("menu.territory.buy_flag.name"),
						GuiUtil.lines("menu.territory.buy_flag.lore", price, guild.treasury)),
				this::buyFlagDenial, () -> result(ClaimService.buyExtensionFlag(player, guild)));
		action(22, icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("menu.territory.founding_flag.name"),
						GuiUtil.lines("menu.territory.founding_flag.lore")).glow(),
				this::foundingFlagDenial, () -> result(ClaimService.giveFoundingFlag(player, guild)));
		button(24, icon(Items.TNT, "menu.territory.abandon.name", GuiUtil.lines("menu.territory.abandon.lore")),
				this::abandonDenial, () -> confirmAbandon(guild));

		if (GuildyServer.config().guilds.homeEnabled) {
			action(30, icon(Items.BED.pick(DyeColor.RED), "menu.territory.sethome.name", GuiUtil.lines("menu.territory.sethome.lore")),
					this::setHomeDenial, () -> result(ClaimService.setHome(player, guild)));
			List<Component> homeLore = guild.home == null ? GuiUtil.lines("menu.territory.home.lore_none")
					: GuiUtil.lines("menu.territory.home.lore", guild.home.getX(), guild.home.getY(), guild.home.getZ());
			button(32, icon(Items.ENDER_PEARL, "menu.territory.home.name", homeLore), this::homeDenial, () -> {
				close();
				ClaimService.teleportHome(player, guild);
			});
		}
		bottomBar(() -> new MainMenu(player, guild).show());
	}

	private List<Component> infoLore(Guild guild) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("menu.territory.info.chunks", guild.claimCount(), guild.maxChunks()));
		if (guild.heart != null) {
			lore.add(Lang.get("menu.territory.info.heart", guild.heart.getX(), guild.heart.getY(), guild.heart.getZ()));
		} else {
			lore.addAll(GuiUtil.lines("menu.territory.info.heart_none"));
		}
		if (GuildyServer.config().guilds.homeEnabled) {
			lore.add(guild.home == null ? Lang.get("menu.territory.info.home_none")
					: Lang.get("menu.territory.info.home", guild.home.getX(), guild.home.getY(), guild.home.getZ()));
		}
		lore.add(Lang.get("menu.territory.info.flag_price", ClaimService.extensionFlagPrice(guild)));
		return lore;
	}

	private List<Component> chunkLore(Guild guild) {
		List<Component> lore = new ArrayList<>();
		if (!inClaimLevel()) {
			lore.add(Lang.get("menu.territory.chunk.wrong_dimension"));
			return lore;
		}
		long chunk = currentChunk();
		Guild owner = GuildManager.atChunk(chunk);
		if (owner == guild) {
			lore.add(Lang.get("menu.territory.chunk.own"));
			if (guild.isFounded() && guild.heartChunk() == chunk) {
				lore.add(Lang.get("menu.territory.chunk.heart"));
			} else if (guild.isBorderChunk(chunk)) {
				lore.add(Lang.get("menu.territory.chunk.border"));
			}
		} else if (owner != null) {
			lore.add(Lang.get("menu.territory.chunk.other", owner.name));
		} else {
			lore.add(Lang.get("menu.territory.chunk.free"));
		}
		return lore;
	}

	private void confirmAbandon(Guild guild) {
		GuiUtil.confirm(player, Lang.get("menu.territory.abandon.confirm.title"),
				GuiUtil.lines("menu.territory.abandon.confirm.lore", guild.claimCount()), () -> {
					Denial denial = abandonDenial();
					if (denial != null) {
						denial.send(player);
						GuiUtil.errorSound(player);
					} else if (result(ClaimService.abandonTerritory(player, guild))) {
						GuiUtil.successSound(player);
					} else {
						GuiUtil.errorSound(player);
					}
					reopen();
				}, this::reopen);
	}

	private void reopen() {
		Guild guild = guild();
		if (guild != null) {
			new TerritoryMenu(player, guild).show();
		} else {
			GuildMenus.open(player);
		}
	}
}
