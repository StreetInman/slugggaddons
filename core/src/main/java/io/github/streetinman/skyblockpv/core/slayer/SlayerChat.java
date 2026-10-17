package io.github.streetinman.skyblockpv.core.slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the slayer-related chat lines Hypixel sends, with colour codes already removed. */
public final class SlayerChat {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** A line of the "[Sacks] +N items." hover: " +12 Wolf Tooth (Combat Sack)". */
	private static final Pattern SACK_LINE = Pattern.compile("^\\s*\\+([\\d,]+) (.+?) \\(([^()]+ Sack)\\)\\s*$");
	/** "Moved 64 Wolf Tooth from your Sacks to your inventory." */
	private static final Pattern FROM_SACKS = Pattern.compile("^Moved ([\\d,]+) (.+?) from your Sacks to your inventory\\.?$");

	public enum Event { QUEST_STARTED, BOSS_SLAIN, QUEST_COMPLETE, QUEST_FAILED }

	private SlayerChat() {
	}

	public static String strip(String text) {
		return FORMATTING.matcher(text).replaceAll("");
	}

	public static Optional<Event> event(String line) {
		String text = strip(line).strip();
		if (text.equals("SLAYER QUEST STARTED!")) return Optional.of(Event.QUEST_STARTED);
		if (text.equals("NICE! SLAYER BOSS SLAIN!")) return Optional.of(Event.BOSS_SLAIN);
		if (text.equals("SLAYER QUEST COMPLETE!")) return Optional.of(Event.QUEST_COMPLETE);
		if (text.startsWith("SLAYER QUEST FAILED!")) return Optional.of(Event.QUEST_FAILED);
		return Optional.empty();
	}

	public static boolean isSackMessage(String line) {
		return strip(line).strip().startsWith("[Sacks] +");
	}

	public record Gain(String name, long count) {
	}

	/** Items added to sacks, from the text of the sack message's hover. */
	public static List<Gain> sackGains(String hover) {
		List<Gain> gains = new ArrayList<>();
		for (String line : strip(hover).split("\n")) {
			Matcher m = SACK_LINE.matcher(line);
			if (m.matches()) gains.add(new Gain(m.group(2).strip(), parseCount(m.group(1))));
		}
		return gains;
	}

	/** Items taken out of sacks with /gfs, which should not count as drops when they reach the inventory. */
	public static Optional<Gain> movedFromSacks(String line) {
		Matcher m = FROM_SACKS.matcher(strip(line).strip());
		return m.matches() ? Optional.of(new Gain(m.group(2), parseCount(m.group(1)))) : Optional.empty();
	}

	/** Best guess at a SkyBlock item ID from its plain name, used when the item list isn't loaded. */
	public static String guessId(String name) {
		return strip(name).strip().toUpperCase(Locale.ROOT).replace('\'', '_').replaceAll("[^A-Z0-9]+", "_");
	}

	private static long parseCount(String digits) {
		return Long.parseLong(digits.replace(",", ""));
	}
}
