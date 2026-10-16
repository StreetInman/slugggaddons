package io.github.streetinman.skyblockpv;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import io.github.streetinman.skyblockpv.compat.Compat;
import io.github.streetinman.skyblockpv.core.api.MojangClient;
import io.github.streetinman.skyblockpv.core.api.ProfileService;
import io.github.streetinman.skyblockpv.core.model.Profile;
import io.github.streetinman.skyblockpv.core.parse.ProfileParser;
import io.github.streetinman.skyblockpv.core.parse.SkillTable;
import io.github.streetinman.skyblockpv.gui.ItemBrowserScreen;
import io.github.streetinman.skyblockpv.gui.PvScreen;
import io.github.streetinman.skyblockpv.gui.SettingsScreen;

/**
 * CI-only (env {@code SKYBLOCKPV_SELFTEST=1}): opens /pv with sample data and clicks through every
 * tab and page, then the item browser and every settings tab. A watchdog prints the render
 * thread's stack and stops the game if a frame takes over 20 seconds, so a freeze fails the build
 * instead of hanging it.
 */
final class SelfTest {
	private static final String UUID = "069a79f444e94726a5befca90e38aaf5";
	private static final int STEP_TICKS = 10;

	private static volatile long lastTick = System.currentTimeMillis();
	private static volatile String stage = "waiting for title screen";
	private static volatile int tick;
	private static volatile int phase;
	private static int step;
	private static int waitStart;
	private static CompletableFuture<ProfileService.Lookup> sample;
	private static volatile net.minecraft.client.gui.screens.Screen screen;

	private SelfTest() {
	}

	static void start() {
		Thread renderThread = Thread.currentThread();
		Thread watchdog = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					return;
				}
				// Startup (before the first tick) gets 4 minutes under software rendering; after that 20 seconds a frame.
				long limit = tick == 0 ? 240_000 : phase > 0 ? 20_000 : 90_000;
				if (System.currentTimeMillis() - lastTick > limit) {
					StringBuilder trace = new StringBuilder();
					for (StackTraceElement e : renderThread.getStackTrace()) trace.append("\n    at ").append(e);
					SkyblockPvClient.LOGGER.error("SKYBLOCKPV SELFTEST STALL during {}{}", stage, trace);
					Runtime.getRuntime().halt(3);
				}
			}
		}, "skyblockpv-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		// The current screen moved between versions, so track it from the screen events instead.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, opened, w, h) -> screen = opened);
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
	}

	private static void tick(Minecraft mc) {
		lastTick = System.currentTimeMillis();
		tick++;
		if (tick % 200 == 1) SkyblockPvClient.LOGGER.info("SKYBLOCKPV SELFTEST tick {} screen {} stage {}", tick, screen, stage);
		try {
			run(mc);
		} catch (RuntimeException e) {
			SkyblockPvClient.LOGGER.error("SKYBLOCKPV SELFTEST FAILED during {}", stage, e);
			mc.stop();
		}
	}

	private static void run(Minecraft mc) {
		switch (phase) {
			case 0 -> {
				// A fresh game shows the accessibility onboarding screen instead of the title screen.
				if (screen instanceof TitleScreen || screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
					sample = sampleLookup();
					next("opening /pv");
				}
			}
			case 1 -> {
				Compat.setScreen(new PvScreen("Notch", SkyblockPvClient.profiles(), SkyblockPvClient.config(), sample));
				next("waiting for /pv data");
			}
			case 2 -> {
				if (screen instanceof PvScreen pv && pv.loaded()) next("clicking through /pv tabs");
				else if (tick - waitStart > 1200) throw new IllegalStateException("/pv never loaded");
			}
			case 3 -> {
				// 9 tabs × 3 pages each, one every STEP_TICKS.
				if (tick % STEP_TICKS != 0) return;
				if (!(screen instanceof PvScreen pv)) throw new IllegalStateException("/pv closed: " + screen);
				int tabs = PvScreen.tabLabels().size();
				if (step >= tabs * 3) {
					SkyblockPvClient.LOGGER.info("SKYBLOCKPV SELFTEST /pv player model shown: {}", pv.showsModel());
					next("opening item browser");
					return;
				}
				stage = "/pv tab " + PvScreen.tabLabels().get(step / 3) + " page " + (step % 3 + 1);
				pv.showTab(step / 3, step % 3);
				step++;
			}
			case 4 -> {
				Compat.setScreen(new ItemBrowserScreen(null, "hyperion"));
				next("waiting for item data");
			}
			case 5 -> {
				if (screen instanceof ItemBrowserScreen items && items.loaded()) {
					SkyblockPvClient.LOGGER.info("SKYBLOCKPV SELFTEST item browser: {}", items.status() == null ? "loaded" : items.status());
					items.selectShown(0);
					next("item browser showing a recipe");
				} else if (tick - waitStart > 4800) {
					throw new IllegalStateException("item data never loaded");
				}
			}
			case 6 -> {
				if (tick - waitStart < 40) return;
				Compat.setScreen(new ItemBrowserScreen(null, ""));
				next("item browser full list");
			}
			case 7 -> {
				if (tick - waitStart < 40) return;
				Compat.setScreen(new SettingsScreen(SkyblockPvClient.config(), () -> { }));
				next("clicking through settings tabs");
			}
			case 8 -> {
				if (tick % STEP_TICKS != 0) return;
				if (!(screen instanceof SettingsScreen settings)) throw new IllegalStateException("settings closed");
				if (step >= SettingsScreen.categoryCount()) {
					next("done");
					return;
				}
				stage = "settings tab " + step;
				settings.showCategory(step++);
			}
			default -> {
				SkyblockPvClient.LOGGER.info("SKYBLOCKPV SELFTEST PASSED");
				phase = -1;
				mc.stop();
			}
		}
	}

	private static void next(String newStage) {
		phase++;
		step = 0;
		waitStart = tick;
		stage = newStage;
		SkyblockPvClient.LOGGER.info("SKYBLOCKPV SELFTEST {}", newStage);
	}

	/** A small but busy profile: pets, floors, slayers, essences, mining and Jacob data. */
	private static CompletableFuture<ProfileService.Lookup> sampleLookup() {
		return SkyblockPvClient.profiles().hypixel().resource("skills").thenApply(skills -> {
			try {
				JsonObject root = JsonParser.parseString(SAMPLE.replace("$UUID", UUID)).getAsJsonObject();
				List<Profile> profiles = ProfileParser.parseProfiles(root, UUID);
				return new ProfileService.Lookup(new MojangClient.PlayerId(UUID, "Notch"), profiles, ProfileParser.selected(profiles),
						SkillTable.fromResource(skills), "Derpy", root);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	private static final String SAMPLE = """
			{"success": true, "profiles": [{"profile_id": "p1", "cute_name": "Mango", "selected": true, "banking": {"balance": 123456789},
			 "members": {"$UUID": {
			  "leveling": {"experience": 34567},
			  "currencies": {"coin_purse": 4567890, "motes_purse": 1200, "essence": {"WITHER": {"current": 5000}, "DRAGON": {"current": 1200}}},
			  "fairy_soul": {"total_collected": 240},
			  "profile": {"first_join": 1600000000000},
			  "player_data": {"experience": {"SKILL_COMBAT": 111672425, "SKILL_MINING": 55172425, "SKILL_FARMING": 25000000, "SKILL_FISHING": 9000000},
			   "crafted_generators": ["WHEAT_1", "WHEAT_2", "COBBLESTONE_1"]},
			  "player_stats": {"deaths": {"total": 1234}, "kills": {"total": 98765}, "highest_critical_damage": 12345678, "items_fished": {"total": 4321},
			   "gifts": {"total_given": 10, "total_received": 20}},
			  "slayer": {"slayer_bosses": {"zombie": {"xp": 1500000}, "spider": {"xp": 400000}, "wolf": {"xp": 1000000}, "enderman": {"xp": 2000000}, "blaze": {"xp": 50000}}},
			  "pets_data": {"pets": [
			   {"type": "GOLDEN_DRAGON", "tier": "LEGENDARY", "exp": 210000000, "active": true, "heldItem": "DWARF_TURTLE_SHELMET"},
			   {"type": "ENDER_DRAGON", "tier": "LEGENDARY", "exp": 25353230, "active": false},
			   {"type": "BEE", "tier": "COMMON", "exp": 1000, "active": false, "skin": "BEE_RGBEE"},
			   {"type": "UNKNOWN_PET", "tier": "WEIRD", "exp": 1, "active": false}]},
			  "accessory_bag_storage": {"highest_magical_power": 1450, "selected_power": "silky", "tuning": {"slot_0": {"health": 10, "strength": 20}}, "unlocked_powers": ["silky"]},
			  "mining_core": {"experience": 4000000, "powder_mithril": 100000, "powder_spent_mithril": 2000000, "powder_gemstone": 50000, "powder_glacite": 10},
			  "nether_island_player_data": {"selected_faction": "mages", "mages_reputation": 12000, "barbarians_reputation": -100,
			   "kuudra_completed_tiers": {"none": 50, "hot": 40, "burning": 30, "fiery": 20, "infernal": 10}},
			  "jacobs_contest": {"medals_inv": {"gold": 3, "silver": 5, "bronze": 9}, "perks": {"double_drops": 15, "farming_level_cap": 10}, "contests": {"a": {}, "b": {}}},
			  "trophy_fish": {"total_caught": 57, "rewards": [1, 2], "blobfish_bronze": 10, "blobfish_silver": 3},
			  "dungeons": {"selected_dungeon_class": "mage", "secrets": 45678,
			   "player_classes": {"healer": {"experience": 1000000}, "mage": {"experience": 300000000}, "berserk": {"experience": 20000000},
			    "archer": {"experience": 50000000}, "tank": {"experience": 5000000}},
			   "dungeon_types": {
			    "catacombs": {"experience": 400000000, "tier_completions": {"0": 5, "1": 10, "5": 30, "7": 500},
			     "fastest_time": {"7": 320000}, "fastest_time_s": {"7": 300000}, "fastest_time_s_plus": {"7": 280000}, "best_score": {"7": 317}},
			    "master_catacombs": {"tier_completions": {"1": 3, "5": 40, "7": 1500}, "fastest_time_s_plus": {"7": 330000}, "best_score": {"7": 312}}}}
			 }, "coopmate": {}}}]}
			""";
}
