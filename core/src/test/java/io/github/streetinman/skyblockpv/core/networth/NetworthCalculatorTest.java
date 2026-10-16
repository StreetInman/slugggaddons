package io.github.streetinman.skyblockpv.core.networth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import io.github.streetinman.skyblockpv.core.TestNbt;
import io.github.streetinman.skyblockpv.core.dungeons.DungeonData;
import io.github.streetinman.skyblockpv.core.model.Inventories;
import io.github.streetinman.skyblockpv.core.model.MemberData;
import io.github.streetinman.skyblockpv.core.model.Pet;
import io.github.streetinman.skyblockpv.core.model.SkyblockItem;
import io.github.streetinman.skyblockpv.core.model.TrophyFishing;
import io.github.streetinman.skyblockpv.core.networth.Networth.Category;
import io.github.streetinman.skyblockpv.core.parse.ItemDecoder;

class NetworthCalculatorTest {
	private static final Prices PRICES = new Prices(
			Map.of("RECOMBOBULATOR_3000", 6_000_000.0, "HOT_POTATO_BOOK", 80_000.0, "FUMING_POTATO_BOOK", 1_000_000.0,
					"ENCHANTMENT_ULTIMATE_WISE_5", 2_000_000.0, "PERFECT_SAPPHIRE_GEM", 15_000_000.0, "FINE_JADE_GEM", 50_000.0,
					"FIRST_MASTER_STAR", 10_000_000.0, "ENCHANTED_DIAMOND", 1_000.0, "ESSENCE_WITHER", 3_000.0),
			Map.of("HYPERION", 470_000_000.0, "IMPLOSION_SCROLL", 30_000_000.0, "ENDER_DRAGON;4", 440_000_000.0,
					"PET_ITEM_TIER_BOOST", 80_000_000.0));

	@Test
	@SuppressWarnings("unchecked")
	void addsEverythingAppliedToAnItem() throws Exception {
		Map<String, Object> hype = TestNbt.enchanted(267, "HYPERION", "ultimate_wise", 5);
		Map<String, Object> extra = (Map<String, Object>) ((Map<String, Object>) hype.get("tag")).get("ExtraAttributes");
		extra.put("rarity_upgrades", 1);
		extra.put("hot_potato_count", 15);
		extra.put("upgrade_level", 6);
		extra.put("ability_scroll", List.of("IMPLOSION_SCROLL"));
		Map<String, Object> gems = new LinkedHashMap<>();
		gems.put("COMBAT_0", "PERFECT");
		gems.put("COMBAT_0_gem", "SAPPHIRE");
		gems.put("JADE_0", new LinkedHashMap<>(Map.of("quality", "FINE", "uuid", "x")));
		extra.put("gems", gems);

		double value = NetworthCalculator.itemValue(decode(hype), PRICES);
		double expected = 470e6 + 2e6 + 6e6 + 10 * 80_000 + 5 * 1e6 + 10e6 + 30e6 + 15e6 + 50_000;
		assertEquals(expected, value, 1e-3);
	}

	@Test
	void countsStacksPetsSacksAndEssence() throws Exception {
		Map<String, Object> diamonds = TestNbt.item(264, "ENCHANTED_DIAMOND", "§aEnchanted Diamond");
		diamonds.put("Count", (byte) 64);
		var inventory = ItemDecoder.decodeInventory(TestNbt.inventory(Arrays.asList(diamonds, null)));
		Inventories inv = new Inventories(inventory, null, null, null, null, null, Map.of(), null, Map.of(),
				Map.of("ENCHANTED_DIAMOND", 100L), Set.of());
		MemberData m = new MemberData("u", 0, 1_000, 0, Map.of(), inv, new TrophyFishing(Map.of(), 0, 0),
				new DungeonData(0, Map.of(), null, 0, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()), Map.of(), Map.of(),
				List.of(new Pet("ENDER_DRAGON", "LEGENDARY", 0, true, "PET_ITEM_TIER_BOOST", null)),
				Map.of("WITHER", 10L), io.github.streetinman.skyblockpv.core.model.AccessoryPower.NONE,
				io.github.streetinman.skyblockpv.core.model.ProfileExtras.NONE, Map.of());

		Networth nw = NetworthCalculator.calculate(m, 5_000.0, PRICES);

		assertEquals(64_000, nw.categories().get(Category.INVENTORY), 1e-6);
		assertEquals(100_000, nw.categories().get(Category.SACKS), 1e-6);
		assertEquals(520_000_000, nw.categories().get(Category.PETS), 1e-6);
		assertEquals(30_000, nw.categories().get(Category.ESSENCE), 1e-6);
		assertEquals(1_000 + 5_000 + 64_000 + 100_000 + 520_000_000 + 30_000, nw.total(), 1e-6);
		assertEquals("§6Ender dragon Pet", nw.topItems().getFirst().name());
	}

	@Test
	void parsesPriceSources() {
		var bazaar = Prices.parseBazaar(JsonParser.parseString(
				"{\"products\": {\"HOT_POTATO_BOOK\": {\"quick_status\": {\"sellPrice\": 79000.5}}}}").getAsJsonObject());
		assertEquals(79000.5, bazaar.get("HOT_POTATO_BOOK"));
		var bin = Prices.parseLowestBin(JsonParser.parseString("{\"HYPERION\": 470000000, \"ENDER_DRAGON;4\": 1}").getAsJsonObject());
		assertEquals(470_000_000.0, bin.get("HYPERION"));
		assertEquals(0, new Prices(bazaar, bin).of("NOPE"));
	}

	private static SkyblockItem decode(Map<String, Object> item) throws Exception {
		return ItemDecoder.decodeInventory(TestNbt.inventory(Arrays.asList(item))).getFirst();
	}
}
