package io.github.streetinman.skyblockpv.core.slayer;

/**
 * SkyBlock calendar maths for the "Mayor" tracker. A SkyBlock year lasts 124 real hours and the
 * newly elected mayor takes office on Late Spring 27th, so each term runs from that day to the
 * same day next year.
 */
public final class MayorTerm {
	/** Start of SkyBlock year 1. */
	static final long EPOCH_MILLIS = 1_560_275_700_000L;
	static final long DAY_MILLIS = 20 * 60 * 1000L;
	static final long YEAR_MILLIS = 372 * DAY_MILLIS;
	/** Late Spring 27th: two 31-day months plus 26 days into the year. */
	static final long TERM_START_OFFSET = (2 * 31 + 26) * DAY_MILLIS;

	private MayorTerm() {
	}

	/** The SkyBlock year in which the current mayor took office. */
	public static long termYear(long nowMillis) {
		return Math.floorDiv(nowMillis - EPOCH_MILLIS - TERM_START_OFFSET, YEAR_MILLIS) + 1;
	}

	public static long termStartMillis(long year) {
		return EPOCH_MILLIS + (year - 1) * YEAR_MILLIS + TERM_START_OFFSET;
	}
}
