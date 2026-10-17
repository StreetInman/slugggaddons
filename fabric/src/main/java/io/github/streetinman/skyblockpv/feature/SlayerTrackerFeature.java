package io.github.streetinman.skyblockpv.feature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.config.PvConfig;
import io.github.streetinman.skyblockpv.core.items.ItemRepository;
import io.github.streetinman.skyblockpv.core.items.RepoItem;
import io.github.streetinman.skyblockpv.core.networth.Prices;
import io.github.streetinman.skyblockpv.core.slayer.SessionRule;
import io.github.streetinman.skyblockpv.core.slayer.SlayerChat;
import io.github.streetinman.skyblockpv.core.slayer.SlayerScoreboard;
import io.github.streetinman.skyblockpv.core.slayer.SlayerTracker;
import io.github.streetinman.skyblockpv.core.slayer.SlayerType;
import io.github.streetinman.skyblockpv.item.ItemCatalog;

/**
 * Slayer drop and profit tracker for every slayer, in any area (the Howling Cave, the Spider's
 * Den, the Crimson Isle...). It only reads: the "Slayer Quest" lines of the sidebar, slayer chat
 * messages, the "[Sacks]" message's hover, and your own inventory. Nothing is sent to the server.
 *
 * <p>Drops count while a slayer quest is running, from the inventory (ignoring changes while a
 * chest or menu is open, so buying and moving items doesn't count) and from sack messages.
 */
public final class SlayerTrackerFeature {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern ENCHANTMENT = Pattern.compile("enchantments:\\{\"?([a-z_]+)\"?:(\\d+)");
	/** Read the sidebar and inventory this often. */
	private static final int SCAN_TICKS = 5;
	/** After a chest or menu closes, inventory changes still come from it for a moment. */
	private static final long CONTAINER_COOLDOWN = 1_500;
	private static final long SAVE_EVERY = 30_000;
	private static final long ELECTION_EVERY = 3_600_000;

	private static SlayerTracker tracker;
	private static Path file;
	private static Map<String, Long> lastInventory;
	private static ClientLevel lastLevel;
	private static long containerClosedAt;
	private static int ticks;
	private static long lastSave;
	/** Items recently moved out of sacks with /gfs: not drops. */
	private static final Map<String, Long> fromSacks = new HashMap<>();
	private static long fromSacksUntil;
	private static volatile double costMultiplier = 1;
	private static volatile String mayorName = "";
	private static long electionFetched;
	private static volatile Prices prices;
	private static long pricesFetched;
	private static volatile Map<String, String> idsByName = Map.of();
	private static volatile ItemRepository repository;
	private static long namesTried;

	private SlayerTrackerFeature() {
	}

	public static void register() {
		file = FabricLoader.getInstance().getConfigDir().resolve("slugggaddons/slayer-tracker.json");
		tracker = new SlayerTracker(load(), new SlayerTracker.Settings() {
			@Override
			public SessionRule sessionRule() {
				return settings().sessionRule();
			}

			@Override
			public int resetHour() {
				return Math.clamp(settings().resetHour, 0, 23);
			}

			@Override
			public int breakMinutes() {
				return Math.max(1, settings().breakMinutes);
			}
		}, () -> costMultiplier);

		ClientTickEvents.END_CLIENT_TICK.register(SlayerTrackerFeature::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onMessage(message);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
			tracker.onJoin(System.currentTimeMillis());
			lastInventory = null;
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> save());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> save());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("slugggaddons", "slayer_tracker"), SlayerTrackerFeature::drawHud);
	}

	public static SlayerTracker tracker() {
		return tracker;
	}

	private static PvConfig.Slayer settings() {
		return SkyblockPvClient.config().slayer;
	}

	// ---- Input ----

	private static void tick(Minecraft mc) {
		if (!settings().enabled || mc.player == null || mc.level == null) {
			lastInventory = null;
			return;
		}
		long now = System.currentTimeMillis();
		boolean container = mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof InventoryScreen);
		if (container) containerClosedAt = now;
		if (++ticks % SCAN_TICKS != 0) return;

		Optional<SlayerType.Boss> quest = SlayerScoreboard.activeQuest(sidebar(mc.level.getScoreboard()));
		tracker.tick(now, quest);
		if (quest.isPresent()) {
			refreshPrices(now);
			refreshElection(now);
			loadNames();
		}

		Map<String, Long> inventory = inventory(mc.player);
		boolean sameWorld = mc.level == lastLevel;
		lastLevel = mc.level;
		if (lastInventory != null && sameWorld && now - containerClosedAt > CONTAINER_COOLDOWN && tracker.counting(now)) {
			for (Map.Entry<String, Long> e : inventory.entrySet()) {
				long gained = e.getValue() - lastInventory.getOrDefault(e.getKey(), 0L);
				if (gained > 0) tracker.addItem(e.getKey(), gained - takeFromSacks(e.getKey(), gained, now), now);
			}
		}
		lastInventory = inventory;

		if (now - lastSave > SAVE_EVERY && tracker.takeDirty()) {
			save();
		}
	}

	private static void onMessage(Component message) {
		if (!settings().enabled || tracker == null) return;
		String text = message.getString();
		long now = System.currentTimeMillis();
		SlayerChat.event(text).ifPresent(event -> tracker.onEvent(event, now));
		SlayerChat.movedFromSacks(text).ifPresent(gain -> {
			fromSacks.merge(idFor(gain.name()), gain.count(), Long::sum);
			fromSacksUntil = now + 5_000;
		});
		if (SlayerChat.isSackMessage(text) && tracker.counting(now)) {
			for (String hover : hoverTexts(message)) {
				for (SlayerChat.Gain gain : SlayerChat.sackGains(hover)) tracker.addItem(idFor(gain.name()), gain.count(), now);
			}
		}
	}

	private static long takeFromSacks(String id, long gained, long now) {
		if (now > fromSacksUntil) {
			fromSacks.clear();
			return 0;
		}
		long moved = fromSacks.getOrDefault(id, 0L);
		long used = Math.min(moved, gained);
		if (used > 0) fromSacks.put(id, moved - used);
		return used;
	}

	private static List<String> hoverTexts(Component message) {
		List<String> texts = new ArrayList<>();
		collectHovers(message, texts);
		return texts;
	}

	private static void collectHovers(Component component, List<String> out) {
		if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component value)) out.add(value.getString());
		for (Component sibling : component.getSiblings()) collectHovers(sibling, out);
	}

	/** Sidebar lines from top to bottom, as shown. */
	private static List<String> sidebar(Scoreboard scoreboard) {
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return List.of();
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
		entries.removeIf(PlayerScoreEntry::isHidden);
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner));
		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			lines.add(PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
		}
		return lines;
	}

	/** SkyBlock item ID → count over the 36 main inventory slots. */
	private static Map<String, Long> inventory(LocalPlayer player) {
		Map<String, Long> counts = new HashMap<>();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty()) continue;
			String id = skyblockId(stack);
			if (id.isEmpty() || id.equals("SKYBLOCK_MENU")) continue;
			counts.merge(id, (long) stack.getCount(), Long::sum);
		}
		return counts;
	}

	private static String skyblockId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return "";
		var tag = data.copyTag();
		String id = tag.getStringOr("id", "");
		if (id.equals("ENCHANTED_BOOK")) {
			// Price books by their enchantment, as the bazaar lists them: ENCHANTMENT_SMITE_7.
			Matcher m = ENCHANTMENT.matcher(tag.toString());
			if (m.find()) return "ENCHANTMENT_" + m.group(1).toUpperCase(Locale.ROOT) + "_" + m.group(2);
		}
		return id;
	}

	// ---- Prices, names and the mayor ----

	private static void refreshPrices(long now) {
		if (now - pricesFetched < 10 * 60_000) return;
		pricesFetched = now;
		SkyblockPvClient.prices().prices().whenComplete((p, error) -> {
			if (p != null) prices = p;
			else SkyblockPvClient.LOGGER.warn("Slayer tracker couldn't load prices: {}", SkyblockPvClient.rootMessage(error));
		});
	}

	private static void refreshElection(long now) {
		if (now - electionFetched < ELECTION_EVERY) return;
		electionFetched = now;
		SkyblockPvClient.hypixel().resource("election").whenComplete((json, error) -> {
			if (json == null) return;
			JsonObject mayor = json.has("mayor") ? json.getAsJsonObject("mayor") : new JsonObject();
			mayorName = mayor.has("name") ? mayor.get("name").getAsString() : "";
			boolean slashed = hasPerk(mayor, "Slashed Pricing")
					|| (mayor.has("minister") && hasPerk(mayor.getAsJsonObject("minister"), "Slashed Pricing"));
			costMultiplier = slashed ? 0.5 : 1;
		});
	}

	private static boolean hasPerk(JsonObject official, String perk) {
		List<JsonElement> perks = new ArrayList<>();
		if (official.has("perks")) official.getAsJsonArray("perks").forEach(perks::add);
		if (official.has("perk")) perks.add(official.get("perk"));
		for (JsonElement p : perks) {
			if (p.isJsonObject() && p.getAsJsonObject().has("name") && perk.equals(p.getAsJsonObject().get("name").getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static void loadNames() {
		long now = System.currentTimeMillis();
		if (repository != null || now - namesTried < 5 * 60_000) return;
		namesTried = now;
		ItemCatalog.repository().thenAccept(repo -> {
			if (repository != null) return;
			repository = repo;
			Map<String, String> map = new HashMap<>();
			for (RepoItem item : repo.all()) map.putIfAbsent(item.plainName().toLowerCase(Locale.ROOT), item.id());
			idsByName = map;
		});
	}

	private static String idFor(String name) {
		String id = idsByName.get(SlayerChat.strip(name).strip().toLowerCase(Locale.ROOT));
		return id != null ? id : SlayerChat.guessId(name);
	}

	private static String nameFor(String id) {
		ItemRepository repo = repository;
		RepoItem item = repo != null ? repo.get(id) : null;
		if (item != null && item.name() != null) return item.name();
		StringBuilder name = new StringBuilder();
		for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) continue;
			if (!name.isEmpty()) name.append(' ');
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return "§f" + name;
	}

	private static double price(String id) {
		Prices p = prices;
		return p != null ? p.of(id) : 0;
	}

	// ---- Output ----

	/** The tracker as display lines, with colour codes. */
	public static List<String> lines(SlayerType type, SlayerTracker.Mode mode, int maxItems) {
		SlayerTracker.Summary s = tracker.summary(type, mode, SlayerTrackerFeature::price);
		List<String> lines = new ArrayList<>();
		String label = mode == SlayerTracker.Mode.MAYOR && !mayorName.isEmpty() ? "Mayor " + mayorName : mode.label;
		lines.add("§6§l" + type.bossName + " §7(" + label + ")");
		lines.add("§7Bosses: §f" + num(s.bosses()) + (s.bossesPerHour() > 0 ? " §8(" + decimal(s.bossesPerHour()) + "/h)" : ""));
		int shown = 0;
		for (SlayerTracker.ItemLine item : s.items()) {
			if (shown++ >= maxItems) {
				lines.add("§8+" + (s.items().size() - maxItems) + " more items");
				break;
			}
			lines.add("§7" + num(item.count()) + "x " + nameFor(item.id()) + (item.total() > 0 ? " §6" + coins(item.total()) : ""));
		}
		if (s.items().isEmpty()) lines.add("§8No drops yet");
		if (s.coinsSpent() > 0) lines.add("§7Quest cost: §c-" + coins(s.coinsSpent()));
		lines.add("§7Profit: " + (s.profit() >= 0 ? "§a" : "§c") + coins(s.profit())
				+ (s.profitPerHour() != 0 ? " §8(" + coins(s.profitPerHour()) + "/h)" : ""));
		if (s.activeMillis() > 0) lines.add("§7Time: §f" + duration(s.activeMillis()));
		if (prices == null) lines.add("§8Prices load during your next quest");
		return lines;
	}

	private static void drawHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		PvConfig.Slayer settings = settings();
		Minecraft mc = Minecraft.getInstance();
		if (tracker == null || !settings.enabled || !settings.showHud) return;
		long now = System.currentTimeMillis();
		if (settings.hudOnlyDuringQuest && !tracker.counting(now)) return;
		Optional<SlayerType> type = tracker.currentType();
		if (type.isEmpty()) return;
		List<String> lines = lines(type.get(), settings.mode(), Math.max(1, settings.maxItems));
		int width = 0;
		for (String line : lines) width = Math.max(width, mc.font.width(line));
		int height = lines.size() * 10 + 4;
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		int x = Math.clamp((int) (screenW * settings.hudX / 100), 0, Math.max(0, screenW - width - 6));
		int y = Math.clamp((int) (screenH * settings.hudY / 100), 0, Math.max(0, screenH - height));
		g.fill(x, y, x + width + 6, y + height, 0x80000000);
		int lineY = y + 3;
		for (String line : lines) {
			g.text(mc.font, line, x + 3, lineY, 0xFFFFFFFF, true);
			lineY += 10;
		}
	}

	public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		for (String root : new String[] {"slayertracker", "sltracker"}) {
			dispatcher.register(ClientCommands.literal(root)
					.executes(ctx -> show(ctx.getSource(), null))
					.then(ClientCommands.literal("mode").executes(ctx -> cycleMode(ctx.getSource())))
					.then(ClientCommands.literal("reset")
							.executes(ctx -> reset(ctx.getSource(), settings().mode().name()))
							.then(ClientCommands.argument("tracker", StringArgumentType.word())
									.suggests((ctx, b) -> {
										for (SlayerTracker.Mode m : SlayerTracker.Mode.values()) b.suggest(m.name().toLowerCase(Locale.ROOT));
										return b.buildFuture();
									})
									.executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "tracker")))))
					.then(ClientCommands.argument("slayer", StringArgumentType.word())
							.suggests((ctx, b) -> {
								for (SlayerType t : SlayerType.values()) b.suggest(t.shortName.toLowerCase(Locale.ROOT));
								return b.buildFuture();
							})
							.executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "slayer")))));
		}
	}

	private static int show(FabricClientCommandSource source, String slayer) {
		Optional<SlayerType> type = slayer != null ? SlayerType.byName(slayer) : tracker.currentType();
		if (type.isEmpty()) {
			source.sendError(Component.literal(slayer != null ? "Unknown slayer: " + slayer
					: "No slayer done yet. Try /slayertracker sven (or rev, tara, eman, blaze, vamp)."));
			return 0;
		}
		refreshPrices(System.currentTimeMillis());
		for (SlayerTracker.Mode mode : SlayerTracker.Mode.values()) {
			for (String line : lines(type.get(), mode, 20)) source.sendFeedback(Component.literal(line));
		}
		return 1;
	}

	private static int cycleMode(FabricClientCommandSource source) {
		PvConfig.Slayer settings = settings();
		settings.mode = settings.mode().next();
		SkyblockPvClient.saveConfig();
		source.sendFeedback(Component.literal("§aSlayer tracker now shows: " + settings.mode.label));
		return 1;
	}

	private static int reset(FabricClientCommandSource source, String which) {
		for (SlayerTracker.Mode mode : SlayerTracker.Mode.values()) {
			if (mode.name().equalsIgnoreCase(which) || mode.label.equalsIgnoreCase(which)) {
				tracker.reset(mode, System.currentTimeMillis());
				save();
				source.sendFeedback(Component.literal("§aReset the " + mode.label + " slayer tracker."));
				return 1;
			}
		}
		source.sendError(Component.literal("Use session, mayor or total."));
		return 0;
	}

	// ---- Saving ----

	private static SlayerTracker.Saved load() {
		try {
			if (Files.exists(file)) {
				SlayerTracker.Saved saved = GSON.fromJson(Files.readString(file), SlayerTracker.Saved.class);
				if (saved != null && saved.session != null && saved.mayor != null && saved.total != null) return saved;
			}
		} catch (Exception e) {
			SkyblockPvClient.LOGGER.warn("Couldn't read {}, starting the slayer tracker fresh", file, e);
		}
		return new SlayerTracker.Saved();
	}

	public static void save() {
		if (tracker == null) return;
		lastSave = System.currentTimeMillis();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(tracker.saved()));
			Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			SkyblockPvClient.LOGGER.warn("Couldn't save the slayer tracker", e);
		}
	}

	// ---- Formatting ----

	private static String num(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	static String coins(double amount) {
		double abs = Math.abs(amount);
		String sign = amount < 0 ? "-" : "";
		if (abs >= 1e9) return sign + String.format(Locale.ROOT, "%.2fB", abs / 1e9);
		if (abs >= 1e6) return sign + String.format(Locale.ROOT, "%.2fM", abs / 1e6);
		if (abs >= 1e3) return sign + String.format(Locale.ROOT, "%.1fk", abs / 1e3);
		return sign + String.format(Locale.ROOT, "%.0f", abs);
	}

	private static String duration(long millis) {
		long minutes = millis / 60_000;
		return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
	}
}
