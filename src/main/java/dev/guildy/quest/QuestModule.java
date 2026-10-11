package dev.guildy.quest;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;
import dev.guildy.structure.StructureBehavior;

/**
 * Quest board and quests.
 */
public final class QuestModule {
	private QuestModule() {
	}

	public static void init() {
		ConfigHandle<QuestConfig> config = ConfigManager.register("quests.json5", QuestConfig.class, QuestConfig::new);
		QuestLang.register();
		QuestManager.init(config);
		TreasureMaps.init();
		QuestCommands.register();
		StructureBehavior.register(QuestManager.BOARD, new QuestBoardBehavior());
		Hooks.openQuests = QuestMenus::openList;
	}
}
