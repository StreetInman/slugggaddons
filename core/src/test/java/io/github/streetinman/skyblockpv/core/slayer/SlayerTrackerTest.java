package io.github.streetinman.skyblockpv.core.slayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SlayerTrackerTest {
	private static final long T0 = 1_800_000_000_000L;

	private SessionRule rule = SessionRule.DAILY;

	private final SlayerTracker.Settings settings = new SlayerTracker.Settings() {
		@Override
		public SessionRule sessionRule() {
			return rule;
		}

		@Override
		public int resetHour() {
			return 0;
		}

		@Override
		public int breakMinutes() {
			return 30;
		}

		@Override
		public ZoneId zone() {
			return ZoneOffset.UTC;
		}
	};

	private static final Optional<SlayerType.Boss> SVEN4 = Optional.of(new SlayerType.Boss(SlayerType.WOLF, 4));

	@Test
	void readsSidebarQuest() {
		List<String> lines = List.of("§707/10/26 §8m12A", " ", "Slayer Quest", "§cSven Packmaster §4IV", "§eSlay the boss!");
		assertEquals(SVEN4, SlayerScoreboard.activeQuest(lines));
		assertEquals(Optional.of(new SlayerType.Boss(SlayerType.ENDERMAN, 3)),
				SlayerScoreboard.activeQuest(List.of("Slayer Quest🍫", "Voidgloom Ser👾aph III")));
		assertEquals(Optional.empty(), SlayerScoreboard.activeQuest(List.of("Purse: 1,000", "Slayer Quest")));
	}

	@Test
	void readsChat() {
		assertEquals(Optional.of(SlayerChat.Event.QUEST_STARTED), SlayerChat.event("  §5§lSLAYER QUEST STARTED!"));
		assertEquals(Optional.of(SlayerChat.Event.BOSS_SLAIN), SlayerChat.event("  §a§lNICE! SLAYER BOSS SLAIN!"));
		assertTrue(SlayerChat.isSackMessage("§6[Sacks] §r§a+12§e items§r. §8(Last 5s.)"));
		assertEquals(List.of(new SlayerChat.Gain("Wolf Tooth", 1234), new SlayerChat.Gain("Enchanted Bone", 2)),
				SlayerChat.sackGains("Added items:\n §a+1,234 §fWolf Tooth §7(Combat Sack)\n §a+2 §aEnchanted Bone §7(Combat Sack)\n\nThis message..."));
		assertEquals(Optional.of(new SlayerChat.Gain("Wolf Tooth", 64)),
				SlayerChat.movedFromSacks("Moved 64 Wolf Tooth from your Sacks to your inventory."));
		assertEquals("WOLF_TOOTH", SlayerChat.guessId("Wolf Tooth"));
	}

	@Test
	void countsBossesDropsAndCost() {
		SlayerTracker tracker = new SlayerTracker(new SlayerTracker.Saved(), settings, () -> 1.0);
		tracker.tick(T0, Optional.empty());
		assertFalse(tracker.counting(T0));
		tracker.addItem("WOLF_TOOTH", 5, T0);

		tracker.onEvent(SlayerChat.Event.QUEST_STARTED, T0 + 100);
		tracker.tick(T0 + 1_000, SVEN4);
		tracker.tick(T0 + 61_000 - 4_000, SVEN4);
		for (long t = T0 + 61_000 - 4_000; t <= T0 + 121_000; t += 1_000) tracker.tick(t, SVEN4);
		tracker.addItem("WOLF_TOOTH", 40, T0 + 121_000);
		tracker.onEvent(SlayerChat.Event.BOSS_SLAIN, T0 + 121_000);
		tracker.tick(T0 + 122_000, Optional.empty());
		tracker.addItem("OVERFLUX_CAPACITOR", 1, T0 + 130_000);
		tracker.addItem("WOLF_TOOTH", 1, T0 + 200_000);

		Map<String, Double> prices = Map.of("WOLF_TOOTH", 100.0, "OVERFLUX_CAPACITOR", 1_000_000.0);
		for (SlayerTracker.Mode mode : SlayerTracker.Mode.values()) {
			SlayerTracker.Summary s = tracker.summary(SlayerType.WOLF, mode, id -> prices.getOrDefault(id, 0.0));
			assertEquals(1, s.bosses(), mode.label);
			assertEquals(50_000, s.coinsSpent());
			assertEquals("OVERFLUX_CAPACITOR", s.items().get(0).id());
			assertEquals(40, s.items().get(1).count());
			assertEquals(1_004_000 - 50_000, s.profit(), 0.001);
			assertEquals(64_000, s.activeMillis(), 1_000);
		}
		assertEquals(Optional.of(SlayerType.WOLF), tracker.currentType());
	}

	@Test
	void sessionRules() {
		SlayerTracker tracker = new SlayerTracker(new SlayerTracker.Saved(), settings, () -> 0.5);
		tracker.onEvent(SlayerChat.Event.QUEST_STARTED, T0);
		tracker.tick(T0, SVEN4);
		tracker.onEvent(SlayerChat.Event.BOSS_SLAIN, T0);
		assertEquals(25_000, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.SESSION, id -> 0).coinsSpent());

		// Next day: the daily session starts over, total keeps counting.
		long tomorrow = T0 + 24 * 3_600_000L;
		tracker.tick(tomorrow, SVEN4);
		assertEquals(0, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.SESSION, id -> 0).bosses());
		assertEquals(1, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.TOTAL, id -> 0).bosses());

		rule = SessionRule.LOGIN;
		tracker.onEvent(SlayerChat.Event.BOSS_SLAIN, tomorrow);
		tracker.onJoin(tomorrow + 1);
		assertEquals(0, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.SESSION, id -> 0).bosses());

		rule = SessionRule.BREAK;
		tracker.onEvent(SlayerChat.Event.BOSS_SLAIN, tomorrow + 2);
		tracker.tick(tomorrow + 10 * 60_000L, SVEN4);
		assertEquals(1, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.SESSION, id -> 0).bosses());
		tracker.tick(tomorrow + 50 * 60_000L, SVEN4);
		assertEquals(0, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.SESSION, id -> 0).bosses());
		assertEquals(3, tracker.summary(SlayerType.WOLF, SlayerTracker.Mode.TOTAL, id -> 0).bosses());
	}

	@Test
	void mayorTermsLastOneSkyblockYear() {
		long year = MayorTerm.termYear(T0);
		long start = MayorTerm.termStartMillis(year);
		assertTrue(start <= T0 && T0 < start + MayorTerm.YEAR_MILLIS);
		assertEquals(year + 1, MayorTerm.termYear(start + MayorTerm.YEAR_MILLIS));
		assertEquals(year, MayorTerm.termYear(start + MayorTerm.YEAR_MILLIS - 1));
	}

	@Test
	void savesAsJson() {
		SlayerTracker.Saved saved = new SlayerTracker.Saved();
		saved.total.of(SlayerType.ZOMBIE).addItem("REVENANT_FLESH", 3);
		saved.lastType = SlayerType.ZOMBIE;
		com.google.gson.Gson gson = new com.google.gson.Gson();
		SlayerTracker.Saved back = gson.fromJson(gson.toJson(saved), SlayerTracker.Saved.class);
		assertEquals(3L, back.total.of(SlayerType.ZOMBIE).items.get("REVENANT_FLESH"));
		assertEquals(SlayerType.ZOMBIE, back.lastType);
	}
}
