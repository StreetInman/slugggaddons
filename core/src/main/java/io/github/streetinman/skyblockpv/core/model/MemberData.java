package io.github.streetinman.skyblockpv.core.model;

import java.util.Map;

import io.github.streetinman.skyblockpv.core.dungeons.DungeonData;

/**
 * One player's data within a profile.
 *
 * @param skillXp  skill name (e.g. {@code FARMING}) → total XP. Empty when skills API is off.
 * @param slayerXp        XP per slayer boss
 * @param attributeStacks hunting-shard ID → shards syphoned into its attribute
 * @param pets            every pet in the pet menu
 * @param essence         essence type (e.g. {@code WITHER}) → amount
 * @param power           magical power, selected power stone and tuning
 * @param extras          smaller stats for the Misc tab
 */
public record MemberData(
		String uuid,
		double skyblockLevel,
		double purse,
		int fairySouls,
		Map<String, Double> skillXp,
		Inventories inventories,
		TrophyFishing trophyFishing,
		DungeonData dungeons,
		Map<Slayer, Long> slayerXp,
		Map<String, Integer> attributeStacks,
		java.util.List<Pet> pets,
		Map<String, Long> essence,
		AccessoryPower power,
		ProfileExtras extras,
		/** Every item list found under the profile's {@code loadout} data, keyed by where it was found. */
		Map<String, java.util.List<SkyblockItem>> loadouts) {
}
