package io.github.streetinman.skyblockpv.core.slayer;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

/**
 * Counts slayer bosses, drops and quest costs into three trackers at once: the current session,
 * the current mayor's term and all time.
 *
 * <p>The game side feeds it what it sees: the sidebar's active quest every tick, slayer chat
 * events, and items gained (from the inventory or from sack messages). Items only count while a
 * slayer quest is running, or shortly after it ends, so it works in any area.
 */
public final class SlayerTracker {
	/** Items that arrive within this long after the quest leaves the sidebar still count. */
	static final long GRACE_MILLIS = 15_000;
	/** Ticks further apart than this (game paused, disconnected) don't add to the active time. */
	static final long MAX_TICK_GAP = 5_000;
	/** The sidebar shows a new quest within this long of "SLAYER QUEST STARTED!". */
	static final long START_WINDOW = 5_000;

	public enum Mode {
		SESSION("Session"), MAYOR("Mayor"), TOTAL("Total");

		public final String label;

		Mode(String label) {
			this.label = label;
		}

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** What's saved to disk. */
	public static final class Saved {
		public TrackerPeriod session = new TrackerPeriod();
		public TrackerPeriod mayor = new TrackerPeriod();
		public TrackerPeriod total = new TrackerPeriod();
		public long lastActivityMillis;
		public SlayerType lastType;
	}

	public interface Settings {
		SessionRule sessionRule();

		/** Local hour (0–23) when a daily or weekly session starts over. */
		int resetHour();

		/** For {@link SessionRule#BREAK}: minutes without a slayer quest before a new session starts. */
		int breakMinutes();

		default ZoneId zone() {
			return ZoneId.systemDefault();
		}
	}

	public record ItemLine(String id, long count, double each, double total) {
	}

	public record Summary(SlayerType type, Mode mode, long startedMillis, int bosses, List<ItemLine> items,
			double dropValue, long coinsSpent, long activeMillis) {
		public double profit() {
			return dropValue - coinsSpent;
		}

		public double profitPerHour() {
			return activeMillis < 60_000 ? 0 : profit() * 3_600_000.0 / activeMillis;
		}

		public double bossesPerHour() {
			return activeMillis < 60_000 ? 0 : bosses * 3_600_000.0 / activeMillis;
		}
	}

	private final Saved saved;
	private final Settings settings;
	/** Share of the quest price actually paid, e.g. 0.5 with Aatrox's Slashed Pricing. */
	private final DoubleSupplier costMultiplier;
	private SlayerType.Boss quest;
	private long lastTick;
	private long graceUntil;
	private long startedAt = Long.MIN_VALUE;
	private boolean dirty;

	public SlayerTracker(Saved saved, Settings settings, DoubleSupplier costMultiplier) {
		this.saved = saved;
		this.settings = settings;
		this.costMultiplier = costMultiplier;
	}

	public Saved saved() {
		return saved;
	}

	/** True once since the last call if anything changed that should be written to disk. */
	public boolean takeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	public Optional<SlayerType.Boss> quest() {
		return Optional.ofNullable(quest);
	}

	/** The slayer whose tracker to show: the running quest, else the last one done. */
	public Optional<SlayerType> currentType() {
		return quest != null ? Optional.of(quest.type()) : Optional.ofNullable(saved.lastType);
	}

	/** Called when the player joins the server. */
	public void onJoin(long now) {
		if (settings.sessionRule() == SessionRule.LOGIN) resetSession(now);
	}

	public void tick(long now, Optional<SlayerType.Boss> sidebarQuest) {
		rollOver(now);
		quest = sidebarQuest.orElse(null);
		if (quest != null) {
			if (settings.sessionRule() == SessionRule.BREAK && saved.lastActivityMillis > 0
					&& now - saved.lastActivityMillis > settings.breakMinutes() * 60_000L) {
				resetSession(now);
			}
			if (saved.lastType != quest.type()) dirty = true;
			saved.lastType = quest.type();
			graceUntil = now + GRACE_MILLIS;
			long gap = now - lastTick;
			if (lastTick > 0 && gap > 0 && gap <= MAX_TICK_GAP) {
				for (TrackerPeriod period : periods()) period.of(quest.type()).activeMillis += gap;
			}
			if (startedAt != Long.MIN_VALUE && now - startedAt <= START_WINDOW) {
				chargeQuest(quest);
				startedAt = Long.MIN_VALUE;
			}
			saved.lastActivityMillis = now;
		}
		lastTick = now;
	}

	public void onEvent(SlayerChat.Event event, long now) {
		switch (event) {
			case QUEST_STARTED -> startedAt = now;
			case BOSS_SLAIN -> currentType().ifPresent(type -> {
				for (TrackerPeriod period : periods()) period.of(type).bosses++;
				dirty = true;
			});
			default -> {
			}
		}
	}

	/** Whether items picked up right now count as slayer drops. */
	public boolean counting(long now) {
		return quest != null || now < graceUntil;
	}

	public void addItem(String id, long count, long now) {
		if (count <= 0 || id == null || id.isEmpty() || !counting(now)) return;
		Optional<SlayerType> type = currentType();
		if (type.isEmpty()) return;
		for (TrackerPeriod period : periods()) period.of(type.get()).addItem(id, count);
		dirty = true;
	}

	public void reset(Mode mode, long now) {
		switch (mode) {
			case SESSION -> resetSession(now);
			case MAYOR -> saved.mayor = new TrackerPeriod(mayorKey(now), now);
			case TOTAL -> saved.total = new TrackerPeriod("total", now);
		}
		dirty = true;
	}

	public TrackerPeriod period(Mode mode) {
		return switch (mode) {
			case SESSION -> saved.session;
			case MAYOR -> saved.mayor;
			case TOTAL -> saved.total;
		};
	}

	public Summary summary(SlayerType type, Mode mode, ToDoubleFunction<String> price) {
		TrackerPeriod period = period(mode);
		TrackerData data = period.peek(type);
		List<ItemLine> items = new ArrayList<>();
		double value = 0;
		for (Map.Entry<String, Long> e : data.items.entrySet()) {
			double each = Math.max(0, price.applyAsDouble(e.getKey()));
			items.add(new ItemLine(e.getKey(), e.getValue(), each, each * e.getValue()));
			value += each * e.getValue();
		}
		items.sort(Comparator.comparingDouble(ItemLine::total).reversed().thenComparing(ItemLine::id));
		return new Summary(type, mode, period.startedMillis, data.bosses, items, value, data.coinsSpent, data.activeMillis);
	}

	private void chargeQuest(SlayerType.Boss boss) {
		long cost = Math.round(boss.type().questCost(boss.tier()) * costMultiplier.getAsDouble());
		if (cost <= 0) return;
		for (TrackerPeriod period : periods()) period.of(boss.type()).coinsSpent += cost;
		dirty = true;
	}

	private void rollOver(long now) {
		if (saved.total.startedMillis == 0) {
			saved.total = new TrackerPeriod("total", now);
			dirty = true;
		}
		String mayor = mayorKey(now);
		if (!mayor.equals(saved.mayor.key)) {
			saved.mayor = new TrackerPeriod(mayor, now);
			dirty = true;
		}
		SessionRule rule = settings.sessionRule();
		String key = rule.periodKey(now, settings.zone(), settings.resetHour());
		if (saved.session.startedMillis == 0 || (!key.isEmpty() && !key.equals(saved.session.key))) resetSession(now);
	}

	private void resetSession(long now) {
		saved.session = new TrackerPeriod(settings.sessionRule().periodKey(now, settings.zone(), settings.resetHour()), now);
		dirty = true;
	}

	private static String mayorKey(long now) {
		return "year " + MayorTerm.termYear(now);
	}

	private List<TrackerPeriod> periods() {
		return List.of(saved.session, saved.mayor, saved.total);
	}
}
