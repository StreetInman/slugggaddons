package io.github.streetinman.skyblockpv.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.streetinman.skyblockpv.core.model.Inventories;
import io.github.streetinman.skyblockpv.core.model.MemberData;
import io.github.streetinman.skyblockpv.core.model.Profile;
import io.github.streetinman.skyblockpv.core.model.TrophyFish;
import io.github.streetinman.skyblockpv.core.model.TrophyFishing;
import io.github.streetinman.skyblockpv.core.model.Wardrobe;
import io.github.streetinman.skyblockpv.core.parse.ProfileParser;

/** Uses synthetic responses shaped like /v2/skyblock/profiles. Swap in real captures as they're collected. */
class ProfileParserTest {
	private static final String UUID = "0123456789abcdef0123456789abcdef";

	@Test
	void parsesSelectedProfileBasics() throws Exception {
		List<Profile> profiles = ProfileParser.parseProfiles(response(member(true), false), UUID);

		assertEquals(2, profiles.size());
		Profile selected = ProfileParser.selected(profiles);
		assertEquals("Mango", selected.cuteName());
		assertEquals(12_345_678.0, selected.bankBalance());
		MemberData m = selected.member();
		assertEquals(245.5, m.skyblockLevel(), 1e-9);
		assertEquals(1_000_000.0, m.purse());
		assertEquals(240, m.fairySouls());
		assertEquals(55_172_425.0, m.skillXp().get("FARMING"));
		assertEquals(Map.of("SHARD_SCARF", 32, "SHARD_BONZO", 4), m.attributeStacks());
		assertEquals(1_234, m.power().magicalPower());
		assertEquals("silky", m.power().selectedPower());
		assertEquals(Map.of("strength", 30, "critical_damage", 12), m.power().tuning());
		assertEquals(2, m.power().unlockedPowers());
		var m7 = m.dungeons().masterFloors().get(7);
		assertEquals(new io.github.streetinman.skyblockpv.core.dungeons.FloorStats(120, 290_000, 300_000, 312_000, 317), m7);
		assertEquals(40, m.dungeons().normalFloors().get(7).completions());
		assertEquals(12345.0 / (3 + 40 + 120), m.dungeons().secretsPerRun(), 1e-9);
		assertEquals(4, m.extras().hotmTier());
		assertEquals(15, m.extras().kuudraTotal());
		assertEquals("mages", m.extras().crimsonFaction());
		assertEquals(2, m.extras().coopMembers());
	}

	@Test
	void decodesArmorEquipmentAndFlagsUnknownKeys() throws Exception {
		Inventories inv = ProfileParser.parseProfiles(response(member(true), false), UUID).get(1).member().inventories();

		assertTrue(inv.apiEnabled());
		// API order is boots-first; we flip it.
		assertEquals("HELMET", inv.armor().get(0).skyblockId());
		assertEquals("BOOTS", inv.armor().get(3).skyblockId());
		assertEquals(List.of("NECKLACE", "CLOAK", "BELT", "GLOVES"),
				inv.equipment().stream().map(i -> i.skyblockId()).toList());
		assertEquals("ENDER_ITEM", inv.backpacks().get(0).getFirst().skyblockId());
		assertTrue(inv.unknownKeys().contains("loadouts_preview"));
	}

	@Test
	void splitsWardrobePagesIntoSlots() throws Exception {
		Wardrobe wardrobe = ProfileParser.parseProfiles(response(member(true), false), UUID).get(1).member().inventories().wardrobe();

		assertEquals(18, wardrobe.slots().size());
		assertEquals(3, wardrobe.equippedSlot());
		var slot2 = wardrobe.slots().get(1);
		assertEquals(2, slot2.number());
		assertEquals("W_HELM_2", slot2.helmet().skyblockId());
		assertEquals("W_BOOTS_2", slot2.boots().skyblockId());
		var slot11 = wardrobe.slots().get(10);
		assertEquals(11, slot11.number());
		assertEquals("W_CHEST_11", slot11.chestplate().skyblockId());
		assertTrue(wardrobe.slots().get(4).isEmpty());
	}

	@Test
	void parsesTrophyFishing() throws Exception {
		TrophyFishing tf = ProfileParser.parseProfiles(response(member(true), false), UUID).get(1).member().trophyFishing();

		assertEquals(57, tf.totalCaught());
		assertEquals(2, tf.rank());
		assertEquals("Silver Hunter", tf.rankName());
		assertEquals(40, tf.count(TrophyFish.BLOBFISH, TrophyFish.Tier.BRONZE));
		assertEquals(TrophyFish.Tier.DIAMOND, tf.bestTier(TrophyFish.GOLDEN_FISH));
		assertNull(tf.bestTier(TrophyFish.VANILLE));
		assertEquals(2, tf.fishAtLeast(TrophyFish.Tier.BRONZE));
		assertEquals(1, tf.fishAtLeast(TrophyFish.Tier.GOLD));
	}

	@Test
	void parsesDungeonsAndSlayers() throws Exception {
		MemberData m = ProfileParser.parseProfiles(response(member(true), false), UUID).get(1).member();
		var d = m.dungeons();

		assertEquals(1.0e8, d.catacombsXp());
		assertEquals(2.0e8, d.classXp().get(io.github.streetinman.skyblockpv.core.dungeons.DungeonClass.MAGE));
		assertEquals(0.0, d.classXp().get(io.github.streetinman.skyblockpv.core.dungeons.DungeonClass.HEALER));
		assertEquals(io.github.streetinman.skyblockpv.core.dungeons.DungeonClass.MAGE, d.selectedClass());
		assertEquals(12345, d.secrets());
		assertEquals(Map.of(0, 3, 7, 40), d.floorCompletions());
		assertEquals(120, d.masterCompletions().get(7));
		assertEquals(312000L, d.masterFastestSPlusMs().get(7));
		assertEquals(163, d.totalRuns());
		assertEquals(5, d.perks().get("cold_efficiency"));

		assertEquals(9, io.github.streetinman.skyblockpv.core.model.Slayer.ZOMBIE.level(m.slayerXp().get(io.github.streetinman.skyblockpv.core.model.Slayer.ZOMBIE)));
		assertEquals(4, io.github.streetinman.skyblockpv.core.model.Slayer.WOLF.level(m.slayerXp().get(io.github.streetinman.skyblockpv.core.model.Slayer.WOLF)));
		assertEquals(0L, m.slayerXp().get(io.github.streetinman.skyblockpv.core.model.Slayer.BLAZE));
	}

	@Test
	void handlesInventoryApiDisabled() throws Exception {
		Inventories inv = ProfileParser.parseProfiles(response(member(false), false), UUID).get(1).member().inventories();
		assertFalse(inv.apiEnabled());
		assertNull(inv.wardrobe());
	}

	@Test
	void noProfilesMeansEmptyList() throws Exception {
		assertTrue(ProfileParser.parseProfiles(response(null, true), UUID).isEmpty());
	}

	@Test
	void findsLoadoutItemListsAnywhereUnderLoadout() {
		JsonObject member = new JsonObject();
		JsonObject loadout = new JsonObject();
		JsonArray slots = new JsonArray();
		JsonObject first = new JsonObject();
		first.add("armor", data(Arrays.asList(TestNbt.item(1, "LOADOUT_HELM", "§6Helm"))));
		first.addProperty("name", "Dungeons");
		slots.add(first);
		loadout.add("slots", slots);
		member.add("loadout", loadout);
		var loadouts = io.github.streetinman.skyblockpv.core.parse.ProfileParser.parseLoadouts(member);
		assertEquals(java.util.Set.of("slots 1 armor"), loadouts.keySet());
		assertEquals("LOADOUT_HELM", loadouts.get("slots 1 armor").getFirst().skyblockId());

		JsonObject skillTree = new JsonObject();
		skillTree.add("experience", obj("mining", 400_000));
		member.add("skill_tree", skillTree);
		assertEquals(400_000, ProfileParser.parseExtras(member, 1).hotmXp());
	}

	private static JsonObject response(JsonObject member, boolean nullProfiles) {
		JsonObject root = new JsonObject();
		root.addProperty("success", true);
		if (nullProfiles) {
			root.add("profiles", com.google.gson.JsonNull.INSTANCE);
			return root;
		}
		JsonArray profiles = new JsonArray();
		profiles.add(profile("a", "Apple", false, new JsonObject()));
		JsonObject mango = profile("b", "Mango", true, member);
		JsonObject banking = new JsonObject();
		banking.addProperty("balance", 12_345_678.0);
		mango.add("banking", banking);
		profiles.add(mango);
		root.add("profiles", profiles);
		return root;
	}

	private static JsonObject profile(String id, String name, boolean selected, JsonObject member) {
		JsonObject p = new JsonObject();
		p.addProperty("profile_id", id);
		p.addProperty("cute_name", name);
		p.addProperty("selected", selected);
		JsonObject members = new JsonObject();
		members.add(UUID, member);
		members.add("coopmate", new JsonObject());
		p.add("members", members);
		return p;
	}

	private static JsonObject member(boolean inventoryApi) {
		JsonObject m = new JsonObject();
		m.add("leveling", obj("experience", 24_550));
		m.add("currencies", obj("coin_purse", 1_000_000));
		m.add("fairy_soul", obj("total_collected", 240));
		JsonObject playerData = new JsonObject();
		playerData.add("experience", obj("SKILL_FARMING", 55_172_425));
		m.add("player_data", playerData);

		JsonObject trophy = new JsonObject();
		trophy.addProperty("total_caught", 57);
		trophy.addProperty("blobfish_bronze", 40);
		trophy.addProperty("blobfish_silver", 10);
		trophy.addProperty("golden_fish_diamond", 1);
		JsonArray rewards = new JsonArray();
		rewards.add(1);
		rewards.add(2);
		trophy.add("rewards", rewards);
		m.add("trophy_fish", trophy);

		m.add("dungeons", com.google.gson.JsonParser.parseString("""
				{"selected_dungeon_class": "mage", "secrets": 12345,
				 "dungeon_types": {
				   "catacombs": {"experience": 1.0e8, "tier_completions": {"0": 3, "7": 40, "total": 43}},
				   "master_catacombs": {"experience": 0, "tier_completions": {"7": 120},
				     "fastest_time": {"7": 290000}, "fastest_time_s": {"7": 300000},
				     "fastest_time_s_plus": {"7": 312000, "best": 312000}, "best_score": {"7": 317}}},
				 "player_classes": {"mage": {"experience": 2.0e8}, "tank": {"experience": 5.0e7}}}
				""").getAsJsonObject());
		JsonObject perks = new JsonObject();
		perks.addProperty("cold_efficiency", 5);
		m.getAsJsonObject("player_data").add("perks", perks);
		JsonObject slayer = com.google.gson.JsonParser.parseString(
				"{\"slayer_bosses\": {\"zombie\": {\"xp\": 1500000}, \"wolf\": {\"xp\": 2000}}}").getAsJsonObject();
		m.add("slayer", slayer);
		m.add("mining_core", com.google.gson.JsonParser.parseString("{\"experience\": 50000, \"powder_mithril\": 10}").getAsJsonObject());
		m.add("nether_island_player_data", com.google.gson.JsonParser.parseString(
				"{\"selected_faction\": \"mages\", \"kuudra_completed_tiers\": {\"none\": 5, \"infernal\": 10}}").getAsJsonObject());
		m.add("accessory_bag_storage", com.google.gson.JsonParser.parseString("""
				{"highest_magical_power": 1234, "selected_power": "silky", "unlocked_powers": ["silky", "bloody"],
				 "tuning": {"slot_0": {"strength": 30, "critical_damage": 12, "health": 0}}}
				""").getAsJsonObject());
		m.add("attributes", com.google.gson.JsonParser.parseString(
				"{\"stacks\": {\"SHARD_SCARF\": 32, \"SHARD_BONZO\": 4}}").getAsJsonObject());

		if (inventoryApi) {
			JsonObject inv = new JsonObject();
			inv.add("inv_contents", data(Arrays.asList(TestNbt.item(1, "STONE_ITEM", "§fStone"))));
			inv.add("inv_armor", data(Arrays.asList(
					TestNbt.item(301, "BOOTS", "Boots"), TestNbt.item(300, "LEGGINGS", "Leggings"),
					TestNbt.item(299, "CHESTPLATE", "Chestplate"), TestNbt.item(298, "HELMET", "Helmet"))));
			inv.add("equipment_contents", data(Arrays.asList(
					TestNbt.item(397, "NECKLACE", "N"), TestNbt.item(397, "CLOAK", "C"),
					TestNbt.item(397, "BELT", "B"), TestNbt.item(397, "GLOVES", "G"))));
			inv.add("wardrobe_contents", data(wardrobePages()));
			inv.addProperty("wardrobe_equipped_slot", 3);
			JsonObject backpacks = new JsonObject();
			backpacks.add("0", data(Arrays.asList(TestNbt.item(368, "ENDER_ITEM", "Pearl"))));
			inv.add("backpack_contents", backpacks);
			inv.add("loadouts_preview", new JsonObject());
			m.add("inventory", inv);
		}
		return m;
	}

	/** Two pages; slot 2 has a full set, slot 11 a chestplate, slot 5 nothing. */
	private static List<Map<String, Object>> wardrobePages() {
		List<Map<String, Object>> items = new ArrayList<>();
		for (int i = 0; i < 72; i++) items.add(null);
		items.set(1, TestNbt.item(298, "W_HELM_2", "h"));
		items.set(1 + 9, TestNbt.item(299, "W_CHEST_2", "c"));
		items.set(1 + 18, TestNbt.item(300, "W_LEGS_2", "l"));
		items.set(1 + 27, TestNbt.item(301, "W_BOOTS_2", "b"));
		items.set(36 + 1 + 9, TestNbt.item(299, "W_CHEST_11", "c"));
		return items;
	}

	private static JsonObject data(List<Map<String, Object>> items) {
		JsonObject o = new JsonObject();
		o.addProperty("type", 0);
		o.addProperty("data", TestNbt.inventory(items));
		return o;
	}

	private static JsonObject obj(String key, Number value) {
		JsonObject o = new JsonObject();
		o.addProperty(key, value);
		return o;
	}
}
