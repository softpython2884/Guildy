package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/** French defaults of the quarries ("Pierre et variantes" of a real mine, see {@link Quarry}). */
final class QuarryLang {
	private QuarryLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// statuses of a miner
		m.put("villager.status.mine.quarry.opening", "&aOuvre l'accès au gradin Y {0}");
		m.put("villager.status.mine.quarry.strip", "&aCarrière · gradin Y {0}, bande {1}/{2} ({3}/{4})");
		m.put("villager.status.mine.quarry.clearing", "&aDégage la surface · gradin Y {0}, bande {1}/{2}");
		m.put("villager.status.mine.quarry.occupied", "&7Attend que {0} s'écarte (ne creuse jamais sous ses pieds) · Y {1}");
		m.put("villager.status.mine.quarry.wait", "&7Attend du travail à « {0} » : les bandes libres sont prises ou sous un gradin en cours");
		// messages
		m.put("villager.mine.quarry.bench_done", "&aCarrière « {0} » : gradin Y {1} terminé ({2} blocs).");
		m.put("villager.mine.quarry.deeper", "&aCarrière « {0} » : tous les gradins jusqu'à Y {1} sont creusés, la carrière descend vers Y {2}.");
		m.put("villager.mine.issue.no_access", "gradin sans accès depuis l'escalier");
		// zone menu summary
		m.put("villager.mine.quarry.info.benches", "&7Gradins : &f{0} ouvert(s)&7, &f{1} terminé(s) &7sur {2} &8(Y {3} → {4})");
		m.put("villager.mine.quarry.info.current", "&7Gradin en cours : &fY {0} &8· &7bandes &f{1}/{2} &8· &7colonnes &f{3}/{4}");
		m.put("villager.mine.quarry.info.quarried", "&7Blocs extraits de la carrière : &f{0}");
		m.put("villager.mine.quarry.info.support", "&7Colonnes de soutien laissées sous des constructions : &f{0}");
		// exploitation view
		m.put("villager.gui.mine.view.bench", "&6&lGradin Y {0}");
		m.put("villager.gui.mine.view.bench.closed", "&8Pas encore atteint par l'escalier");
		m.put("villager.gui.mine.view.bench.later", "&8Ouvert quand le gradin du dessus avance");
		m.put("villager.gui.mine.view.bench.cells", "&7Colonnes creusées : &f{0}/{1}");
		m.put("villager.gui.mine.view.bench.strips", "&7Bandes terminées : &f{0}/{1}");
		m.put("villager.gui.mine.view.bench.quarried", "&7Blocs extraits : &f{0}");
		m.put("villager.gui.mine.view.bench.skipped", "&7Colonnes laissées (eau, éboulement...) : &c{0}");
		m.put("villager.gui.mine.view.quarry", "&7Carrière : escalier gardé en tour (mur d'un\n&7bloc, une ouverture par gradin) et chaussée\n&7de roche jusqu'au bord ; tout le reste du carré\n&7est creusé par gradins de {0} blocs.");
		Lang.addDefaults(m);
	}
}
