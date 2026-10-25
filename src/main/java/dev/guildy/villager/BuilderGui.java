package dev.guildy.villager;

import dev.guildy.builds.BuildProject;
import dev.guildy.builds.BuildSite;
import dev.guildy.builds.BuildSites;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder parts of the villager menus: its site and role lines, the site button (Auto / a site of the guild) and the
 * role button (Auto / Construire / Récolter), and the "Chantiers" button of the villager list.
 */
final class BuilderGui {
	private BuilderGui() {
	}

	/** Role, site and statistics lines of a builder's icon. */
	static void addLore(List<Component> lore, Guild guild, VillagerRecord record) {
		BuilderData data = record.builder;
		if (record.task == Task.BUILDER) {
			Builders.Note note = Builders.noteOf(guild, record.uuid);
			boolean manual = data != null && BuilderRoles.Role.parse(data.role) != null;
			BuilderRoles.Role role = manual ? BuilderRoles.Role.parse(data.role) : note == null ? null : note.preferred();
			String roleName = role == null ? Lang.raw("villager.builder.role.auto") : Lang.raw("villager.builder.role." + role.id());
			lore.add(Lang.get("villager.gui.lore.builder.role", roleName, Lang.raw(manual ? "villager.builder.role.manual" : "villager.builder.role.distributed")));
			if (note != null && note.working() != null && note.working() != role) {
				lore.add(Lang.get("villager.gui.lore.builder.fallback", Lang.raw("villager.builder.role." + note.working().id())));
			}
			BuildProject project = data == null || data.project < 0 ? null : guild.builds.get(data.project);
			lore.add(Lang.get("villager.gui.lore.builder.site", project == null ? Lang.raw("villager.builder.site.auto") : project.name));
		}
		if (data != null && (data.placed > 0 || data.cleared > 0)) {
			lore.add(Lang.get("villager.gui.lore.stat.built", data.placed, data.cleared, data.fetched));
		}
	}

	/** "Chantier" button: Auto, or one of the guild's unfinished sites (left: next, right: previous). */
	static GuiElementBuilder projectButton(ServerPlayer player, Guild guild, VillagerRecord record, Runnable reopen) {
		BuilderData data = Builders.data(record);
		BuildProject chosen = data.project < 0 ? null : guild.builds.get(data.project);
		List<BuildProject> sites = new ArrayList<>();
		for (BuildProject project : guild.builds.ordered()) {
			if (!project.finished) {
				sites.add(project);
			}
		}
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.builder.site.lore"));
		lore.add(Component.empty());
		for (BuildProject project : sites) {
			BuildSite site = BuildSites.peek(guild, project);
			int percent = site != null && site.scanned() ? site.counts().percent() : project.percent();
			lore.add(Lang.get(project == chosen ? "villager.gui.builder.site.line.chosen" : "villager.gui.builder.site.line", project.name, percent));
		}
		if (sites.isEmpty()) {
			lore.add(Lang.get("villager.gui.builder.site.none"));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.builder.site.click"));
		String title = chosen == null ? Lang.raw("villager.builder.site.auto") : chosen.name;
		return GuiUtil.item(chosen == null ? Items.COMPASS : Items.SCAFFOLDING, Lang.get("villager.gui.builder.site", title), lore)
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
						return;
					}
					if (type.shift) {
						GuiUtil.clickSound(player);
						Hooks.openBuilds.accept(player, guild);
						return;
					}
					int index = chosen == null ? -1 : sites.indexOf(chosen);
					int next = type.isRight ? index - 1 : index + 1;
					if (next < -1) {
						next = sites.size() - 1;
					}
					if (next >= sites.size()) {
						next = -1;
					}
					BuildProject picked = next < 0 ? null : sites.get(next);
					data.project = picked == null ? -1 : picked.id;
					GuildManager.markDirty();
					TaskEngine.resetTask(record.uuid);
					Msg.send(player, "villager.builder.site.set", record.name,
							picked == null ? Lang.raw("villager.builder.site.auto") : picked.name);
					GuiUtil.clickSound(player);
					reopen.run();
				});
	}

	/** "Rôle" button: Auto (the guild's distribution) / Construire / Récolter. */
	static GuiElementBuilder roleButton(ServerPlayer player, Guild guild, VillagerRecord record, Runnable reopen) {
		BuilderData data = Builders.data(record);
		BuilderRoles.Role manual = BuilderRoles.Role.parse(data.role);
		Builders.Note note = Builders.noteOf(guild, record.uuid);
		String title = manual == null ? Lang.raw("villager.builder.role.auto")
				+ (note == null ? "" : " (" + Lang.raw("villager.builder.role." + note.preferred().id()) + ")") : Lang.raw("villager.builder.role." + manual.id());
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.builder.role.lore"));
		if (note != null && note.working() != null) {
			lore.add(Lang.get("villager.gui.builder.role.now", Lang.raw("villager.builder.role." + note.working().id())));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.builder.role.click"));
		return GuiUtil.item(manual == null ? Items.COMPARATOR : manual == BuilderRoles.Role.BUILD ? Items.BRICKS : Items.IRON_PICKAXE,
				Lang.get("villager.gui.builder.role", title), lore).setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
				return;
			}
			BuilderRoles.Role[] roles = BuilderRoles.Role.values();
			int index = manual == null ? -1 : manual.ordinal();
			int next = type.isRight ? index - 1 : index + 1;
			if (next < -1) {
				next = roles.length - 1;
			}
			if (next >= roles.length) {
				next = -1;
			}
			BuilderRoles.Role picked = next < 0 ? null : roles[next];
			data.role = picked == null ? "" : picked.id();
			GuildManager.markDirty();
			Msg.send(player, "villager.builder.role.set", record.name,
					Lang.raw(picked == null ? "villager.builder.role.auto" : "villager.builder.role." + picked.id()));
			GuiUtil.clickSound(player);
			reopen.run();
		});
	}

	/** "Chantiers" button of the villager list. */
	static GuiElementBuilder sitesButton(ServerPlayer player, Guild guild) {
		int builders = Builders.builders(guild).size();
		return GuiUtil.item(Items.SCAFFOLDING, Lang.get("villager.gui.builds"),
				GuiUtil.lines("villager.gui.builds.lore", guild.builds.activeCount(), builders)).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			Hooks.openBuilds.accept(player, guild);
		});
	}
}
