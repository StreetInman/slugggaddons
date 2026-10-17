package io.github.streetinman.skyblockpv.core.slayer;

import java.util.List;
import java.util.Optional;

/** Finds the active slayer quest in the sidebar lines (top to bottom, colour codes removed). */
public final class SlayerScoreboard {
	private SlayerScoreboard() {
	}

	/** The quest shown under "Slayer Quest", if any. */
	public static Optional<SlayerType.Boss> activeQuest(List<String> lines) {
		for (int i = 0; i < lines.size() - 1; i++) {
			if (!ascii(lines.get(i)).equals("Slayer Quest")) continue;
			return SlayerType.parseBoss(ascii(lines.get(i + 1)));
		}
		return Optional.empty();
	}

	/** Hypixel pads sidebar lines with emoji and other invisible characters; the parts we read are plain ASCII. */
	static String ascii(String line) {
		return SlayerChat.strip(line).replaceAll("[^ -~]", "").strip();
	}
}
