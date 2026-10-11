package dev.guildy.quest;

/**
 * One item of a COLLECTION quest with its own progress (persisted with the quest).
 */
public class QuestPart {
	/** Item id or #tag. */
	public String target = "";
	/** Display name (recommended for tags); empty = the game's translated name. */
	public String targetName = "";
	public int amount = 1;
	public int progress;

	public QuestPart() {
	}

	public QuestPart(String target, String targetName, int amount) {
		this.target = target;
		this.targetName = targetName;
		this.amount = amount;
	}

	public int remaining() {
		return Math.max(0, amount - progress);
	}

	public boolean isDone() {
		return progress >= amount;
	}

	QuestPart copy() {
		QuestPart copy = new QuestPart(target, targetName, amount);
		copy.progress = progress;
		return copy;
	}

	/** Fixes nulls and out-of-range values after loading. */
	void normalize() {
		if (target == null) {
			target = "";
		}
		if (targetName == null) {
			targetName = "";
		}
		amount = Math.max(1, amount);
		progress = Math.max(0, Math.min(amount, progress));
	}
}
