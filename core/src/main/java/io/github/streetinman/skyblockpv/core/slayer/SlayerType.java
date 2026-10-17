package io.github.streetinman.skyblockpv.core.slayer;

import java.util.Locale;
import java.util.Optional;

/** The six slayer bosses, as Maddox and the scoreboard name them. */
public enum SlayerType {
	ZOMBIE("Revenant Horror", "Revenant", 2_000, 7_500, 20_000, 50_000, 100_000),
	SPIDER("Tarantula Broodfather", "Tarantula", 2_000, 7_500, 20_000, 50_000, 100_000),
	WOLF("Sven Packmaster", "Sven", 2_000, 7_500, 20_000, 50_000),
	ENDERMAN("Voidgloom Seraph", "Voidgloom", 2_000, 7_500, 20_000, 50_000),
	BLAZE("Inferno Demonlord", "Inferno", 10_000, 25_000, 60_000, 150_000),
	/** Paid in motes, not coins, so it has no coin cost. */
	VAMPIRE("Riftstalker Bloodfiend", "Riftstalker");

	public final String bossName;
	public final String shortName;
	private final long[] questCost;

	SlayerType(String bossName, String shortName, long... questCost) {
		this.bossName = bossName;
		this.shortName = shortName;
		this.questCost = questCost;
	}

	/** Coins Maddox charges to start a quest of this tier (1-based), before mayor discounts. */
	public long questCost(int tier) {
		return tier >= 1 && tier <= questCost.length ? questCost[tier - 1] : 0;
	}

	/** A boss and tier read from a scoreboard line such as "Sven Packmaster IV". */
	public record Boss(SlayerType type, int tier) {
	}

	public static Optional<Boss> parseBoss(String line) {
		String text = line.strip();
		for (SlayerType type : values()) {
			int at = text.indexOf(type.bossName);
			if (at < 0) continue;
			String rest = text.substring(at + type.bossName.length()).strip();
			int space = rest.indexOf(' ');
			String numeral = space < 0 ? rest : rest.substring(0, space);
			return Optional.of(new Boss(type, roman(numeral)));
		}
		return Optional.empty();
	}

	public static Optional<SlayerType> byName(String name) {
		for (SlayerType type : values()) {
			if (type.name().equalsIgnoreCase(name) || type.shortName.equalsIgnoreCase(name)) return Optional.of(type);
		}
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "rev", "revenant", "zombie" -> Optional.of(ZOMBIE);
			case "tara", "tarantula", "spider" -> Optional.of(SPIDER);
			case "sven", "wolf" -> Optional.of(WOLF);
			case "eman", "voidgloom", "enderman" -> Optional.of(ENDERMAN);
			case "blaze", "inferno" -> Optional.of(BLAZE);
			case "vamp", "vampire", "riftstalker" -> Optional.of(VAMPIRE);
			default -> Optional.empty();
		};
	}

	static int roman(String numeral) {
		return switch (numeral) {
			case "I", "1" -> 1;
			case "II", "2" -> 2;
			case "III", "3" -> 3;
			case "IV", "4" -> 4;
			case "V", "5" -> 5;
			default -> 0;
		};
	}
}
