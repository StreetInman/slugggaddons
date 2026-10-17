package io.github.streetinman.skyblockpv.core.slayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Everything counted for one slayer type in one period. Field names are the saved JSON. */
public final class TrackerData {
	public int bosses;
	/** SkyBlock item ID → how many dropped. */
	public Map<String, Long> items = new LinkedHashMap<>();
	public long coinsSpent;
	/** Time spent with a slayer quest running. */
	public long activeMillis;

	public void addItem(String id, long count) {
		items.merge(id, count, Long::sum);
	}

	public boolean isEmpty() {
		return bosses == 0 && items.isEmpty() && coinsSpent == 0;
	}
}
