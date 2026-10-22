package dev.guildy.ruin;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French texts of the dread form of the guardians (Menace VII and more) and of the Avatar (lang.json, '&' colour codes).
 */
final class DreadLang {
	private DreadLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// The dread form
		m.put("ruin.dread.name", "{0} &4&lRedoutable");
		m.put("ruin.dread.bar", "{0} &8— &4&lForme redoutable");
		m.put("ruin.dread.awaken.subtitle", "&4{0} &cs'éveille sous sa forme redoutable…");
		m.put("ruin.dread.awaken.chat", "&4☠ &cForme redoutable &8({0}&8) &7: &c{1} ❤&7 de vie effective, &c+{2} %&7 de dégâts sur vous, et des attaques que vous n'avez jamais vues. &8Chacune est annoncée : lisez le sol !");
		m.put("ruin.dread.awaken.cursed", "&4☠ &5La malédiction s'ajoute à sa forme redoutable &8({0}&8) &7: &c{1} ❤&7 de vie effective, &c+{2} %&7 de dégâts sur vous.");
		m.put("ruin.dread.rooted", "&8☠ &7Des mains osseuses vous &cagrippent&7 : impossible de bouger !");
		m.put("ruin.dread.imprisoned", "&8☠ &7La cage d'os s'est refermée sur vous !");
		m.put("ruin.dread.stunned", "&6☀ &eÉtourdi par le rugissement !");
		m.put("ruin.dread.chain.taut", "&3⛓ &bLa chaîne se tend : revenez à moins de {0} blocs du gardien !");
		m.put("ruin.dread.storm.sand", "&6☁ &eLa tempête de sable s'abat ! Restez dans l'œil (cercle vert).");
		m.put("ruin.dread.storm.ice", "&b❄ &fLe blizzard s'abat ! Restez sous le dôme (cercle vert).");
		m.put("ruin.dread.mummy.title", "&6&l☥ Malédiction de la momie");
		m.put("ruin.dread.mummy.subtitle", "&eÉloignez-vous des autres joueurs !");
		// Warnings of the dread attacks (action bar)
		m.put("ruin.dread.warn.grasping_graves", "&8☠ &7Des tombes s'ouvrent sous vos pieds : &cécartez-vous du cercle rouge !");
		m.put("ruin.dread.warn.bone_prison", "&8☠ &7Une cage d'os se referme autour de vous : &csortez du cercle avant qu'elle ne claque !");
		m.put("ruin.dread.warn.bone_spikes", "&8☠ &7Des pointes d'os vont jaillir le long des &clignes rouges&7 : quittez-les !");
		m.put("ruin.dread.warn.arcane_barrage", "&9✦ &7Un barrage arcanique vous prend pour cible : &ccourez en travers&7, les projectiles tournent mal !");
		m.put("ruin.dread.warn.mirror_clones", "&9✦ &7L'Archimage se dédouble : &ctrouvez le vrai&7 parmi ses reflets !");
		m.put("ruin.dread.warn.gravity_well", "&9✦ &7Un puits de gravité s'ouvre : &cluttez pour ne pas être aspiré&7 vers son centre !");
		m.put("ruin.dread.warn.quicksand", "&6☁ &7Des sables mouvants s'ouvrent : &cne vous laissez pas engloutir&7, éloignez-vous du centre !");
		m.put("ruin.dread.warn.mummy_curse", "&6☥ &eVous êtes maudit : &céloignez-vous des autres&7 avant l'explosion !");
		m.put("ruin.dread.warn.mummy_curse.others", "&6☥ &e{0} &7est maudit : &céloignez-vous de lui&7 !");
		m.put("ruin.dread.warn.sandstorm", "&6☁ &eTempête de sable imminente : &arejoignez le cercle vert&7 (l'œil de la tempête) !");
		m.put("ruin.dread.warn.ice_spikes", "&b❄ &7Des pics de glace vont jaillir le long des &clignes rouges&7 : quittez-les !");
		m.put("ruin.dread.warn.frozen_ground", "&b❄ &7Le sol gèle sous vos pieds : &csortez des cercles rouges&7 !");
		m.put("ruin.dread.warn.blizzard_dome", "&b❄ &fBlizzard imminent : &aabritez-vous dans le cercle vert&7 !");
		m.put("ruin.dread.warn.feather_storm", "&2➹ &7Tempête de plumes : &cplacez-vous entre les branches de la spirale&7 !");
		m.put("ruin.dread.warn.dive_bomb", "&2➹ &7Le Serpent vous a marqué : &cquittez le cercle avant qu'il ne plonge&7 !");
		m.put("ruin.dread.warn.poison_rain", "&2☣ &7Pluie de poison : &cévitez les cercles rouges&7 !");
		m.put("ruin.dread.warn.pounce_chain", "&6☀ &7Le Jaguar vous a marqué : &cquittez le cercle avant son bond&7 !");
		m.put("ruin.dread.warn.stunning_roar", "&6☀ &7Il va rugir : &csortez du cône rouge&7 ou vous serez étourdi !");
		m.put("ruin.dread.warn.solar_beam", "&6☀ &7Le rayon solaire vous vise : &ccourez en travers de la ligne&7 !");
		m.put("ruin.dread.warn.lava_geysers", "&c♨ &7Un geyser de lave va jaillir sous vous : &cécartez-vous&7 !");
		m.put("ruin.dread.warn.magma_meteors", "&c☄ &7Pluie de météores de magma : &cévitez les cercles rouges&7 !");
		m.put("ruin.dread.warn.molten_ring", "&c♨ &7Un anneau de lave va déferler : &aplacez-vous dans une brèche&7 (poteaux verts) !");
		m.put("ruin.dread.warn.soul_chains", "&3⛓ &7Des chaînes d'âmes vous lient au gardien : &crestez à moins de {0} blocs&7 !");
		m.put("ruin.dread.warn.wither_aura", "&8☠ &7Une aura de flétrissure se lève : &céloignez-vous du gardien&7 !");
		m.put("ruin.dread.warn.soul_eruption", "&3☄ &7Éruption d'âmes : &aplacez-vous dans une brèche&7 (poteaux verts) !");
		m.put("ruin.dread.warn.soul_pillars", "&3♨ &7Des piliers de feu des âmes vont jaillir : &csortez des cercles rouges&7 !");
		m.put("ruin.dread.warn.wither_skulls", "&8☠ &7Des têtes de wither vous poursuivent : &ccourez en travers&7 !");
		m.put("ruin.dread.warn.nether_rifts", "&4☄ &7Des failles du Nether se déchirent : &cdes créatures vont en sortir&7 !");
		m.put("ruin.dread.warn.ghast_rain", "&4☄ &7Pluie de boules de feu : &cévitez les cercles rouges&7 !");
		m.put("ruin.dread.warn.collapsing_ring", "&4☄ &7L'anneau de feu des âmes se referme sur le gardien : &aplacez-vous dans une brèche&7 !");

		// The Avatar
		m.put("ruin.avatar.name", "&4&lAvatar infernal &8» {0}");
		m.put("ruin.avatar.name.final", "&4&lAvatar ascendant &8» {0}");
		m.put("ruin.avatar.bar", "&4&l☠ Avatar infernal &8— {0} &8— {1} &8— &cPhase {2}");
		m.put("ruin.avatar.bar.final", "&4&l☠ Avatar ascendant &8— {0} &8— {1} &8— &4&lForme finale");
		m.put("ruin.avatar.bar.transition", "&4&l☠ {0} &cse consume… &8— &cPhase {1} &4&l☠");
		m.put("ruin.avatar.bar.rising", "&4&l☠ {0} &cs'élève au-delà de la mort… &4&l☠");
		m.put("ruin.avatar.awaken.title", "&4&l☠ L'AVATAR S'ÉVEILLE ☠");
		m.put("ruin.avatar.awaken.subtitle", "{0} &cconsume l'étoile du Nether…");
		m.put("ruin.avatar.awaken.chat", "&4☠ &cL'étoile du Nether a nourri la malédiction : {0}&c se lève en &4&lAvatar infernal&c (&f{1}&c) ! &c{2} ❤&7 de vie effective, au moins {3}&7, trois phases puis sa &4forme ascendante&7. Ses attaques mêlent son domaine aux âmes et au Nether ; chacune est annoncée : &elisez le sol.");
		m.put("ruin.avatar.awaken.announce", "&4&l☠ Le ciel rougit… &c{0}&c s'est levé en &4&lAvatar infernal&c dans la &f{1}&c !");
		m.put("ruin.avatar.risen.title", "&4&l☠ ASCENSION ☠");
		m.put("ruin.avatar.risen.subtitle", "{0} &crefuse de tomber…");
		m.put("ruin.avatar.risen.chat", "&4☠ {0}&c s'élève une dernière fois : sa &4&lforme ascendante&c va déchaîner toutes ses attaques. Abattez-la !");
		m.put("ruin.avatar.risen.back.title", "&4&lFORME ASCENDANTE !");
		m.put("ruin.avatar.risen.back.subtitle", "{0} &cdéchaîne le Nether");
		m.put("ruin.avatar.risen.back.chat", "&4&l☠ {0} &cest revenu, plus grand, plus rapide, plus fort : toutes ses attaques, plus vite. &6Le trésor de l'Avatar tombera à sa chute.");
		m.put("ruin.avatar.victory.title", "&6&lL'AVATAR EST TOMBÉ !");
		m.put("ruin.avatar.victory.subtitle", "{0} &7retourne au néant");
		m.put("ruin.avatar.victory.announce", "&6&l☠ &e{0} &7ont abattu l'&4&lAvatar infernal&7 de {1} &8(&f{2}&8) &7: &6butin mythique !");
		m.put("ruin.avatar.vanish", "&4☠ {0}&c retourne au Nether : le ciel s'éclaircit. &8(L'étoile est perdue.)");
		m.put("ruin.avatar.trophy.name", "&4&lCœur de l'Avatar");
		m.put("ruin.avatar.trophy.lore", "&7Le cœur encore brûlant de l'Avatar\n&7de {0}&7 (&f{1}&7), {2}&7.\n&8Abattu par : &e{3}\n&8Trophée, sans autre usage.");
		m.put("ruin.avatar.star.stirs", "&4☠ &cL'étoile du Nether frémit dans le cercle… &7Le rituel maudit va la consumer.");
		m.put("ruin.avatar.star.consumed", "&4☠ &cL'étoile du Nether est consumée : l'Avatar s'éveille !");

		// Admin
		m.put("ruin.admin.dread.used", "Capacité redoutable {0} lancée.");
		m.put("ruin.admin.dread.failed", "&cImpossible d'utiliser {0} maintenant (pas de combat, pas de cible, gardien figé). Capacités : {1}");
		m.put("ruin.admin.avatar.awakened", "&4Avatar &7de {0} &7réveillé (&f{1}&7).");
		m.put("ruin.admin.avatar.not", "&cLe combat de la ruine la plus proche n'est pas celui d'un Avatar.");
		m.put("ruin.admin.info.dread", "&7Forme redoutable : {0} &8| &7vie effective &c{1}&7/&c{2} &8(x{3}) &8| &7dégâts sur les joueurs &cx{4}");
		m.put("ruin.admin.info.dread.none", "&7Forme redoutable : &8non (à partir de Menace {0})");
		m.put("ruin.admin.dread.on", "&4oui");
		m.put("ruin.admin.dread.avatar", "&4&lAvatar");
		Lang.addDefaults(m);
	}
}
