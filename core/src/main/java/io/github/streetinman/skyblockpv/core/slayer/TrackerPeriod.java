package io.github.streetinman.skyblockpv.core.slayer;

import java.util.EnumMap;
import java.util.Map;

/** One tracker mode's counts for every slayer, since {@code startedMillis}. */
public final class TrackerPeriod {
	/** What this period belongs to, e.g. a date for a daily session or the mayor's year. */
	public String key = "";
	public long startedMillis;
	public Map<SlayerType, TrackerData> slayers = new EnumMap<>(SlayerType.class);

	public TrackerPeriod() {
	}

	public TrackerPeriod(String key, long startedMillis) {
		this.key = key;
		this.startedMillis = startedMillis;
	}

	public TrackerData of(SlayerType type) {
		return slayers.computeIfAbsent(type, t -> new TrackerData());
	}

	public TrackerData peek(SlayerType type) {
		TrackerData data = slayers.get(type);
		return data != null ? data : new TrackerData();
	}
}
