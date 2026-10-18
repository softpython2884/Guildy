package dev.guildy.stats;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

import java.util.ArrayList;
import java.util.List;

/**
 * Section {@code stats} of ruins.json5: the "Bilan du combat" sent to the players of a ruin fight. Free of Minecraft classes
 * (unit tests); {@link #fix} clamps every value.
 */
@Json5.Section
public final class FightStatsSettings {
	public static final List<String> DEFAULT_CATEGORIES = List.of("guardian", "guards", "escort", "healing", "shield", "tank", "totems", "final_blow");
	/** The titles of the first version (1.8.1), replaced by {@link #DEFAULT_CATEGORIES} when left untouched. */
	static final List<String> V1_CATEGORIES = List.of("guardian", "guards", "escort", "healing", "shield", "tank", "final_blow");
	/** Version of this section: 2 = the "totems" title (Increvable). */
	public static final int CURRENT_VERSION = 2;

	@Comment("Bilan du combat actif (false = rien n'est suivi ni envoye)")
	public boolean enabled = true;

	@Comment({
			"Titres du chat, dans cet ordre : guardian (Fleau du gardien : degats au gardien), guards (Briseur de gardes : degats",
			"aux gardiens de salle), escort (Chasseur de sbires : degats a l'escorte), healing (Ange gardien : soins donnes aux",
			"autres joueurs), shield (Protecteur : absorption donnee), tank (Rempart : degats encaisses), totems (Increvable :",
			"totems d'immortalite consommes), final_blow (Coup fatal), kills (Moissonneur : monstres tues), spells (Arcaniste :",
			"sorts lances). Le menu du detail montre toujours tout"
	})
	public List<String> categories = new ArrayList<>(DEFAULT_CATEGORIES);

	@Comment({
			"Points minimum (degats infliges, soins, absorption donnee ou degats encaisses) pour figurer dans le bilan (0 a 10000 ;",
			"0 = tous les joueurs passes dans l'arene). Les joueurs presents a la fin le recoivent quand meme"
	})
	public double minDamage = 1;

	@Comment("Minutes pendant lesquelles chacun peut rouvrir son dernier bilan avec /guild bilan (1 a 1440)")
	public int keepMinutes = 30;

	@Comment("Bilan court aussi quand le combat echoue (combat reinitialise faute de joueurs, gardien disparu, admin reset)")
	public boolean onFailure = true;

	@Comment("Joueurs suivis au maximum par combat (2 a 64)")
	public int maxPlayers = 32;

	@Comment("Version de cette section (geree automatiquement, ne pas modifier)")
	public int version = 0;

	/**
	 * Clamps every value; unknown categories are dropped (with a warning). A section of an older version gets the titles
	 * added since, when its list is still the default one of that version.
	 */
	public void fix(List<String> warnings) {
		if (categories == null) {
			categories = new ArrayList<>(DEFAULT_CATEGORIES);
		}
		List<String> known = new ArrayList<>();
		for (String id : categories) {
			FightCategory category = FightCategory.byId(id);
			if (category == null) {
				warnings.add("stats.categories: unknown category '" + id + "' ignored");
			} else if (!known.contains(category.id)) {
				known.add(category.id);
			}
		}
		if (version < 2 && known.equals(V1_CATEGORIES)) {
			known = new ArrayList<>(DEFAULT_CATEGORIES);
		}
		categories = known;
		version = CURRENT_VERSION;
		minDamage = Double.isFinite(minDamage) ? Math.max(0, Math.min(10_000, minDamage)) : 1;
		keepMinutes = Math.max(1, Math.min(1440, keepMinutes));
		maxPlayers = Math.max(2, Math.min(64, maxPlayers));
	}

	public List<FightCategory> categoryList() {
		return FightCategory.parse(categories);
	}
}
