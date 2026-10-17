package io.github.streetinman.skyblockpv.core.slayer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** When the "Session" tracker starts over. */
public enum SessionRule {
	LOGIN("Each time you join"),
	DAILY("Every day"),
	WEEKLY("Every week"),
	BREAK("After a break"),
	MANUAL("Only when you reset it");

	public final String label;

	SessionRule(String label) {
		this.label = label;
	}

	public SessionRule next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/**
	 * The calendar period {@code now} falls in, for the daily and weekly rules: the session resets
	 * when this changes. A day starts at {@code resetHour} local time; a week on Monday at that hour.
	 * Other rules have no calendar period and return "".
	 */
	public String periodKey(long nowMillis, ZoneId zone, int resetHour) {
		LocalDate day = Instant.ofEpochMilli(nowMillis).atZone(zone).minusHours(resetHour).toLocalDate();
		return switch (this) {
			case DAILY -> day.toString();
			case WEEKLY -> "week of " + day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			default -> "";
		};
	}
}
