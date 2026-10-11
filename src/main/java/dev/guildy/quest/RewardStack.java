package dev.guildy.quest;

/**
 * An item reward rolled at quest generation (persisted with the quest).
 */
public class RewardStack {
	public String item = "";
	public int count = 1;

	public RewardStack() {
	}

	public RewardStack(String item, int count) {
		this.item = item;
		this.count = count;
	}
}
