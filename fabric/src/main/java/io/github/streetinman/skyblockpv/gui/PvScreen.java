package io.github.streetinman.skyblockpv.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.compat.Compat;
import io.github.streetinman.skyblockpv.config.PvConfig;
import io.github.streetinman.skyblockpv.core.api.ProfileService;
import io.github.streetinman.skyblockpv.core.dungeons.DungeonCalculator;
import io.github.streetinman.skyblockpv.core.dungeons.DungeonClass;
import io.github.streetinman.skyblockpv.core.dungeons.DungeonData;
import io.github.streetinman.skyblockpv.core.dungeons.DungeonLevels;
import io.github.streetinman.skyblockpv.core.dungeons.XpBoosts;
import io.github.streetinman.skyblockpv.core.model.Inventories;
import io.github.streetinman.skyblockpv.core.model.MemberData;
import io.github.streetinman.skyblockpv.core.model.Profile;
import io.github.streetinman.skyblockpv.core.model.SkillLevel;
import io.github.streetinman.skyblockpv.core.model.SkyblockItem;
import io.github.streetinman.skyblockpv.core.model.Slayer;
import io.github.streetinman.skyblockpv.core.model.TrophyFish;
import io.github.streetinman.skyblockpv.core.model.TrophyFishing;
import io.github.streetinman.skyblockpv.core.networth.Networth;
import io.github.streetinman.skyblockpv.core.networth.NetworthCalculator;
import io.github.streetinman.skyblockpv.core.networth.Prices;
import io.github.streetinman.skyblockpv.item.ItemStacks;

/**
 * The /pv window: a panel with tabs along the top. Data loads asynchronously; until then the
 * panel shows "Loading…" or the error.
 */
public class PvScreen extends Screen {
	private static final int PANEL_WIDTH = 370;
	/** Player model column to the left of the panel; dropped when the window is too narrow. */
	private static final int SIDEBAR_WIDTH = 86;
	private static final int MODEL_HEIGHT = 120;
	private static final int SIDEBAR_GAP = 4;
	private static final int BORDER = 0xFF3A3A5C;
	private static final int ACCENT = 0xFF6C6CFF;
	private static final int BACKGROUND = 0xE0101018;
	private static final int PETS_PER_PAGE = 28;
	private static final int PANEL_HEIGHT = 220;
	private static final int SLOT = 18;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int ACCESSORIES_PER_PAGE = 54;
	/** The API stores every ender chest page back to back; the game shows 45 slots per page. */
	private static final int ENDER_CHEST_PAGE = 45;
	// GLFW key codes; LWJGL isn't on the compile classpath for every Minecraft version.
	private static final int KEY_ENTER = 257;
	private static final int KEY_KP_ENTER = 335;
	private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/** Switches tab and page, as clicking would. Used by the launch self-test. */
	public void showTab(int index, int newPage) {
		tab = Tab.values()[Math.floorMod(index, Tab.values().length)];
		page = Math.floorMod(newPage, pageCount());
		rebuildWidgets();
	}

	public boolean showsModel() {
		return skinWidget != null;
	}

	public boolean loaded() {
		return profile != null || error != null;
	}

	/** Tab labels in order, for the settings screen's "default tab" option. */
	public static List<String> tabLabels() {
		return java.util.Arrays.stream(Tab.values()).map(t -> t.label).toList();
	}

	private enum Tab {
		OVERVIEW("Stats"), SKILLS("Skills"), DUNGEONS("Dungeons"), INVENTORY("Inv"), STORAGE("Storage"),
		WARDROBE("Wardrobe"), ACCESSORIES("Accs"), PETS("Pets"), TROPHY_FISH("Trophy"), MISC("Misc");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	/** Calculator results, computed once per profile since the class simulation loops per run. */
	private record DungeonPlan(XpBoosts boosts, int runsToCata50, DungeonCalculator.ClassPlan classPlan) {
	}

	/** One flippable page of the Storage tab: an ender chest page or a backpack. */
	private record StoragePage(String title, List<SkyblockItem> items) {
	}

	private final String requestedName;
	private final ProfileService service;
	private final PvConfig config;
	private final ItemStacks.Cache stacks = new ItemStacks.Cache();
	private ProfileService.Lookup lookup;
	private Profile profile;
	private DungeonPlan dungeonPlan;
	private List<StoragePage> wardrobePages = List.of();
	private List<StoragePage> storagePages = List.of();
	private Prices prices;
	private Networth networth;
	private String networthError;
	private String error;
	private Tab tab;
	private int page;
	private EditBox searchBox;
	private String searchText = "";

	private int left;
	private int top;
	/** Left edge of the whole window: the sidebar if shown, else the panel. */
	private int frameLeft;
	private boolean showSidebar;
	private SkinPreview skin;
	private net.minecraft.client.gui.components.PlayerSkinWidget skinWidget;
	private List<io.github.streetinman.skyblockpv.core.model.Pet> sortedPets = List.of();
	private ItemStack hoveredStack;
	private final List<net.minecraft.client.gui.components.AbstractWidget> panelWidgets = new ArrayList<>();
	private List<Component> hoveredText;

	public PvScreen(String playerName, ProfileService service, PvConfig config) {
		this(playerName, service, config, service.lookup(playerName));
	}

	/** Shows an already-started (or finished) lookup; the launch self-test uses this with sample data. */
	public PvScreen(String playerName, ProfileService service, PvConfig config, java.util.concurrent.CompletableFuture<ProfileService.Lookup> pending) {
		super(Component.literal("Profile Viewer: " + playerName));
		this.requestedName = playerName;
		this.service = service;
		this.config = config;
		this.tab = java.util.Arrays.stream(Tab.values()).filter(t -> t.label.equalsIgnoreCase(config.gui.defaultTab)).findFirst().orElse(Tab.OVERVIEW);
		pending.whenCompleteAsync((result, failure) -> {
			if (failure != null) {
				error = SkyblockPvClient.rootMessage(failure);
				SkyblockPvClient.LOGGER.warn("Lookup for {} failed", playerName, failure);
			} else {
				lookup = result;
				selectProfile(result.selected());
				try {
					skin = new SkinPreview(result.player().uuid(), result.player().name());
				} catch (RuntimeException e) {
					SkyblockPvClient.LOGGER.warn("Couldn't set up the player model", e);
				}
			}
			rebuildWidgets();
		}, Minecraft.getInstance());
		SkyblockPvClient.prices().prices().whenCompleteAsync((result, failure) -> {
			if (failure != null) {
				networthError = SkyblockPvClient.rootMessage(failure);
				SkyblockPvClient.LOGGER.warn("Couldn't load prices for networth", failure);
			} else {
				prices = result;
				if (profile != null) networth = NetworthCalculator.calculate(profile.member(), profile.bankBalance(), prices);
			}
		}, Minecraft.getInstance());
	}

	private void selectProfile(Profile p) {
		profile = p;
		page = 0;
		MemberData m = p.member();
		XpBoosts boosts = XpBoosts.detect(m.dungeons(), m.inventories(), m.attributeStacks(), lookup.mayor(),
				1 + config.dungeonGlobalBoostPercent / 100.0, config.dungeonGraduateLevel, config.dungeonExplorerLevel,
				config.dungeonExtraClassBoostPercent / 100.0, config.dungeonTeamShare);
		DungeonData d = m.dungeons();
		dungeonPlan = new DungeonPlan(boosts,
				DungeonCalculator.runsToCatacombs50(d.catacombsXp(), d.masterCompletions().getOrDefault(7, 0), DungeonCalculator.M7_BASE_XP, boosts),
				DungeonCalculator.classAverage50(d.classXp(), DungeonCalculator.M7_BASE_XP, boosts));
		storagePages = storagePages(m.inventories());
		wardrobePages = wardrobePages(m);
		sortedPets = m.pets().stream().sorted(java.util.Comparator
				.comparing(io.github.streetinman.skyblockpv.core.model.Pet::active).reversed()
				.thenComparing(java.util.Comparator.comparingInt(io.github.streetinman.skyblockpv.core.model.Pet::tierIndex).reversed())
				.thenComparing(java.util.Comparator.comparingDouble((io.github.streetinman.skyblockpv.core.model.Pet pet) -> io.github.streetinman.skyblockpv.core.model.PetLevels.level(pet)).reversed()))
				.toList();
		networth = prices == null ? null : NetworthCalculator.calculate(m, p.bankBalance(), prices);
	}

	private static List<StoragePage> storagePages(Inventories inv) {
		List<StoragePage> pages = new ArrayList<>();
		List<SkyblockItem> ender = inv.enderChest();
		if (ender != null) {
			for (int from = 0, n = 1; from < ender.size(); from += ENDER_CHEST_PAGE, n++) {
				pages.add(new StoragePage("Ender Chest " + n, ender.subList(from, Math.min(ender.size(), from + ENDER_CHEST_PAGE))));
			}
		}
		// Backpack keys are 0-based slot numbers in the storage menu.
		inv.backpacks().forEach((slot, items) -> pages.add(new StoragePage("Backpack " + (slot + 1), items)));
		return List.copyOf(pages);
	}

	@Override
	protected void init() {
		showSidebar = config.gui.showSidebar && width >= PANEL_WIDTH + SIDEBAR_WIDTH + SIDEBAR_GAP + 10;
		int frameWidth = PANEL_WIDTH + (showSidebar ? SIDEBAR_WIDTH + SIDEBAR_GAP : 0);
		frameLeft = (width - frameWidth) / 2;
		left = frameLeft + (showSidebar ? SIDEBAR_WIDTH + SIDEBAR_GAP : 0);
		// Keep the tab row on screen: a button drawn partly off screen crashes the game (scissor out of bounds).
		top = Math.max(23, (height - PANEL_HEIGHT) / 2 + 10);

		panelWidgets.clear();
		// Tabs share the whole window width: each gets its label width plus an equal share of what's left.
		int gap = 2;
		int labels = 0;
		for (Tab t : Tab.values()) labels += font.width(t.label);
		// Normal padding, or less when the window is too narrow for every label at full padding.
		int padding = Math.max(2, Math.min(6, (frameWidth - labels - gap * (Tab.values().length - 1)) / Tab.values().length));
		int natural = labels + padding * Tab.values().length;
		int spare = Math.max(0, frameWidth - natural - gap * (Tab.values().length - 1));
		int x = frameLeft;
		for (int i = 0; i < Tab.values().length; i++) {
			Tab t = Tab.values()[i];
			int w = font.width(t.label) + padding + spare / Tab.values().length;
			// Give rounding leftovers to the last tab so the row ends exactly at the window edge.
			if (i == Tab.values().length - 1) w = frameLeft + frameWidth - x;
			addRenderableWidget(Button.builder(Component.literal(t.label), b -> {
				tab = t;
				page = 0;
				rebuildWidgets();
			}).bounds(x, top - 22, w, 20).build()).active = t != tab;
			x += w + gap;
		}

		skinWidget = null;
		if (showSidebar && skin != null) {
			skinWidget = panelWidget(skin.widget(SIDEBAR_WIDTH - 8, MODEL_HEIGHT));
			skinWidget.setPosition(frameLeft + 4, top + 4);
		}

		// Look up someone else without closing the window.
		searchBox = new EditBox(font, left + PANEL_WIDTH - 200, top + 4, 100, 16, Component.literal("Player name"));
		searchBox.setMaxLength(16);
		searchBox.setHint(Component.literal("§7Search player…"));
		searchBox.setValue(searchText);
		searchBox.setResponder(text -> searchText = text);
		panelWidget(searchBox);
		panelWidget(Button.builder(Component.literal("Go"), b -> search())
				.bounds(left + PANEL_WIDTH - 98, top + 4, 24, 16).build());

		if (lookup != null && lookup.profiles().size() > 1) {
			panelWidget(Button.builder(Component.literal("Profile ▸"), b -> {
				int i = lookup.profiles().indexOf(profile);
				selectProfile(lookup.profiles().get((i + 1) % lookup.profiles().size()));
				rebuildWidgets();
			}).bounds(left + PANEL_WIDTH - 70, top + 4, 66, 16).build());
		}

		int pages = pageCount();
		if (pages > 1) {
			panelWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages - 1) % pages;
				rebuildWidgets();
			}).bounds(left + PANEL_WIDTH - 50, top + PANEL_HEIGHT - 20, 20, 16).build());
			panelWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages;
				rebuildWidgets();
			}).bounds(left + PANEL_WIDTH - 26, top + PANEL_HEIGHT - 20, 20, 16).build());
		}
	}

	private void search() {
		String name = searchText.trim();
		if (name.isEmpty()) return;
		Compat.setScreen(new PvScreen(name, service, config));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused()
				&& (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER)) {
			search();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		hoveredStack = null;
		hoveredText = null;

		frame(g, left, top, PANEL_WIDTH, PANEL_HEIGHT);
		if (showSidebar) drawSidebar(g, mouseX, mouseY);
		g.text(font, lookup != null ? lookup.player().name() : requestedName, left + 6, top + 6, WHITE, true);
		for (var widget : panelWidgets) widget.extractRenderState(g, mouseX, mouseY, delta);

		if (error != null) {
			g.centeredText(font, "§c" + error, left + PANEL_WIDTH / 2, top + PANEL_HEIGHT / 2, WHITE);
		} else if (profile == null) {
			g.centeredText(font, "Loading…", left + PANEL_WIDTH / 2, top + PANEL_HEIGHT / 2, GREY);
		} else {
			String mode = profile.gameMode() == null ? "" : " §7(" + profile.gameMode() + ")";
			g.text(font, "§a" + profile.cuteName() + mode, left + 6, top + 18, WHITE, false);
			int contentTop = top + 32;
			switch (tab) {
				case OVERVIEW -> drawOverview(g, contentTop, mouseX, mouseY);
				case SKILLS -> drawSkills(g, contentTop);
				case DUNGEONS -> drawDungeons(g, contentTop, mouseX, mouseY);
				case INVENTORY -> drawInventory(g, contentTop, mouseX, mouseY);
				case STORAGE -> drawStorage(g, contentTop, mouseX, mouseY);
				case WARDROBE -> drawWardrobe(g, contentTop, mouseX, mouseY);
				case ACCESSORIES -> drawAccessories(g, contentTop, mouseX, mouseY);
				case PETS -> drawPets(g, contentTop, mouseX, mouseY);
				case TROPHY_FISH -> drawTrophyFish(g, contentTop);
				case MISC -> drawMisc(g, contentTop);
			}
			if (pageCount() > 1) {
				g.text(font, "Page " + (page + 1) + "/" + pageCount(), left + PANEL_WIDTH - 110, top + PANEL_HEIGHT - 16, GREY, false);
			}
		}

		if (hoveredStack != null) {
			g.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
		} else if (hoveredText != null) {
			g.setComponentTooltipForNextFrame(font, hoveredText, mouseX, mouseY);
		}
	}

	private void drawOverview(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		MemberData m = profile.member();
		DungeonData d = m.dungeons();
		double average = skills().stream().mapToInt(SkillLevel::level).average().orElse(0);
		List<String> stats = List.of(
				"§bSkyBlock Level: §f" + decimal(m.skyblockLevel()),
				"§6Networth: " + (networth != null ? "§f" + compact((long) networth.total()) + " §8(hover)"
						: networthError != null ? "§7unavailable" : "§7loading…"),
				"§6Purse: §f" + coins(m.purse()),
				"§6Bank: §f" + (profile.bankBalance() == null ? "§7API off / none" : coins(profile.bankBalance())),
				"§dFairy Souls: §f" + m.fairySouls(),
				"§eSkill Average: §f" + (skills().isEmpty() ? "§7API off" : decimal(average)),
				"§cCatacombs: §f" + decimal(d.catacombsLevel()),
				"§cClass Average: §f" + decimal(d.classAverage()),
				"§3Trophy Fish: §f" + m.trophyFishing().totalCaught() + " §7(" + m.trophyFishing().rankName() + ")");
		drawLines(g, stats, left + 8, y);
		// The networth line is the second one.
		if (mouseX >= left + 8 && mouseX < left + 8 + font.width(stats.get(1)) && mouseY >= y + 11 && mouseY < y + 22) {
			hoveredText = networthTooltip();
		}

		List<String> slayers = new ArrayList<>();
		slayers.add("§5Slayers");
		long totalSlayerXp = 0;
		for (Slayer s : Slayer.values()) {
			long xp = m.slayerXp().getOrDefault(s, 0L);
			totalSlayerXp += xp;
			int level = s.level(xp);
			slayers.add((level == s.maxLevel() ? "§6" : "§f") + s.displayName + " " + level + " §7" + compact(xp) + " XP");
		}
		slayers.add("§7Total: " + compact(totalSlayerXp) + " XP");
		drawLines(g, slayers, Math.max(left + 180, left + 8 + widest(stats) + 16), y);

		drawLines(g, accessoryLines(m), left + 8, y + stats.size() * 11 + 6);
	}

	/** Stat colour, symbol and short name for tuning points, keyed by the API's stat names. */
	private static final Map<String, String> TUNING_STATS = Map.of(
			"health", "§c❤", "defense", "§a❈", "walk_speed", "§f✦", "strength", "§c❁",
			"critical_damage", "§9☠", "critical_chance", "§9☣", "attack_speed", "§e⚔", "intelligence", "§b✎");

	private List<String> accessoryLines(MemberData m) {
		var power = m.power();
		List<String> lines = new ArrayList<>();
		lines.add("§9Accessories");
		lines.add("§7Magical Power: §f" + String.format(Locale.ROOT, "%,d", power.magicalPower()));
		lines.add("§7Power: §f" + (power.selectedPower() == null ? "§8none" : titleCase(power.selectedPower()))
				+ " §8(" + power.unlockedPowers() + " learned)");
		StringBuilder tuning = new StringBuilder();
		power.tuning().forEach((stat, points) -> tuning.append(TUNING_STATS.getOrDefault(stat, "§7" + stat))
				.append("§f").append(points).append(' '));
		lines.add("§7Tuning: " + (tuning.isEmpty() ? "§8none" : tuning.toString().trim()));
		m.pets().stream().filter(io.github.streetinman.skyblockpv.core.model.Pet::active).findFirst().ifPresent(pet ->
				lines.add("§7Active Pet: §f" + titleCase(pet.type()) + " §8(" + titleCase(pet.tier()) + ")"));
		return lines;
	}

	private static String titleCase(String key) {
		if (key == null || key.isEmpty()) return "?";
		StringBuilder out = new StringBuilder();
		for (String word : key.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	private void drawSkills(GuiGraphicsExtractor g, int y) {
		List<SkillLevel> skills = skills();
		if (skills.isEmpty()) {
			g.text(font, "§7Skills API is turned off for this player.", left + 8, y, WHITE, false);
			return;
		}
		int col = 0;
		for (SkillLevel skill : skills) {
			int x = left + 8 + col * 160;
			String name = skill.skill().charAt(0) + skill.skill().substring(1).toLowerCase(Locale.ROOT);
			g.text(font, (skill.maxed() ? "§6" : "§f") + name + " " + skill.level(), x, y, WHITE, false);
			drawBar(g, x, y + 10, 140, skill.progress(), skill.maxed());
			if (++col == 2) {
				col = 0;
				y += 22;
			}
		}
	}

	private void drawDungeons(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		DungeonData d = profile.member().dungeons();
		if (page == 1) {
			drawFloors(g, y, d);
			return;
		}
		int x = left + 8;

		List<String> classLines = new ArrayList<>();
		for (DungeonClass c : DungeonClass.values()) {
			double level = d.classLevel(c);
			String marker = c == d.selectedClass() ? " §a◆" : "";
			classLines.add((level >= 50 ? "§6" : "§f") + c.displayName() + " " + decimal(level) + marker);
		}
		// Bars start after the widest class line so long names never run into them.
		int barX = x + widest(classLines) + 6;
		int barWidth = 50;

		Long best = d.masterFastestSPlusMs().get(7);
		List<String> stats = List.of(
				"§7Secrets: §f" + String.format(Locale.ROOT, "%,d", d.secrets()) + " §8(" + decimal(d.secretsPerRun()) + "/run)",
				"§7Runs: §f" + String.format(Locale.ROOT, "%,d", d.totalRuns())
						+ " §7(M7: §f" + d.masterCompletions().getOrDefault(7, 0) + "§7)",
				"§7Best M7 S+: §f" + (best == null ? "—" : time(best)));
		int columnEnd = Math.max(barX + barWidth, x + widest(stats));

		double cata = d.catacombsLevel();
		g.text(font, "§cCatacombs " + decimal(cata), x, y, WHITE, false);
		drawBar(g, x, y + 10, columnEnd - x, cata >= 50 ? 1 : cata - Math.floor(cata), cata >= 50);
		y += 18;
		for (int i = 0; i < classLines.size(); i++) {
			double level = d.classLevel(DungeonClass.values()[i]);
			g.text(font, classLines.get(i), x, y, WHITE, false);
			drawBar(g, barX, y + 2, barWidth, level >= 50 ? 1 : level - Math.floor(level), level >= 50);
			y += 11;
		}
		g.text(font, "§eClass Average " + decimal(d.classAverage()), x, y + 2, WHITE, false);
		y += 16;
		drawLines(g, stats, x, y);

		drawCalculator(g, columnEnd + 14, top + 32, mouseX, mouseY);
	}

	private void drawCalculator(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		int startY = y;
		g.text(font, "§6§lM7 Calculator", x, y, WHITE, false);
		y += 13;

		if (dungeonPlan.runsToCata50() > 0) {
			g.text(font, "§fCata 50: §e" + String.format(Locale.ROOT, "%,d", dungeonPlan.runsToCata50()) + " runs", x, y, WHITE, false);
			y += 13;
		}

		DungeonCalculator.ClassPlan plan = dungeonPlan.classPlan();
		if (plan.totalRuns() > 0) {
			g.text(font, "§fClass Avg 50: §e" + String.format(Locale.ROOT, "%,d", plan.totalRuns()) + " runs", x, y, WHITE, false);
			y += 11;
			for (DungeonClass c : DungeonClass.values()) {
				int runs = plan.runsAs().get(c);
				String line = runs == 0 ? "§7" + c.displayName() + ": §apassive only" : "§7" + c.displayName() + ": §f" + String.format(Locale.ROOT, "%,d", runs);
				g.text(font, line, x + 6, y, WHITE, false);
				y += 10;
			}
			y += 3;
		}

		if (dungeonPlan.runsToCata50() == 0 && plan.totalRuns() == 0) {
			g.text(font, "§aCata 50 and class average 50 done!", x, y, WHITE, false);
			y += 13;
		}

		XpBoosts b = dungeonPlan.boosts();
		List<String> boostLines = new ArrayList<>();
		boostLines.add("§7Hecatomb " + ROMAN[Math.min(b.hecatombLevel(), 10)] + (b.expertRing() ? " · Expert Ring" : ""));
		boostLines.add("§7Scarf +" + percent(b.scarfBonus()) + " · Graduate " + ROMAN[b.graduateLevel()]);
		if (b.explorerLevel() > 0 || b.mayorMultiplier() > 1) {
			boostLines.add("§7" + (b.explorerLevel() > 0 ? "Explorer " + ROMAN[b.explorerLevel()] : "")
					+ (b.explorerLevel() > 0 && b.mayorMultiplier() > 1 ? " · " : "") + (b.mayorMultiplier() > 1 ? "Derpy" : ""));
		}
		boostLines.add("§8Hover for details");
		drawLines(g, boostLines, x, y);
		y += boostLines.size() * 11;

		if (mouseX >= x && mouseX < left + PANEL_WIDTH && mouseY >= startY && mouseY < y) {
			hoveredText = calculatorTooltip(b);
		}
	}

	private List<Component> networthTooltip() {
		List<Component> lines = new ArrayList<>();
		if (networth == null) {
			lines.add(Component.literal(networthError != null ? "§cCouldn't load prices: " + networthError : "§7Loading prices…"));
			return lines;
		}
		lines.add(Component.literal("§6Networth: §f" + coins(networth.total())));
		for (Map.Entry<Networth.Category, Double> e : networth.categories().entrySet()) {
			if (e.getValue() >= 1) lines.add(Component.literal("§7" + e.getKey().displayName + ": §f" + compact(e.getValue().longValue())));
		}
		if (!networth.topItems().isEmpty()) {
			lines.add(Component.literal(""));
			lines.add(Component.literal("§eMost valuable:"));
			for (Networth.Valued v : networth.topItems()) {
				lines.add(Component.literal("§f" + v.name() + " §7" + compact((long) v.value())));
			}
		}
		lines.add(Component.literal(""));
		lines.add(Component.literal("§8Estimate from lowest BIN and bazaar prices,"));
		lines.add(Component.literal("§8plus enchants, books, gems, stars and scrolls."));
		return lines;
	}

	private List<Component> calculatorTooltip(XpBoosts b) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("§6How this is calculated"));
		lines.add(Component.literal("§7S+ M7 runs (300k base XP), always playing"));
		lines.add(Component.literal("§7the class furthest from 50. Classes you"));
		lines.add(Component.literal("§7don't play get " + percent(b.teamShare()) + " of their XP."));
		lines.add(Component.literal(""));
		lines.add(Component.literal("§eBoosts found on this profile:"));
		lines.add(Component.literal("§7Hecatomb: §f" + ROMAN[Math.min(b.hecatombLevel(), 10)] + " §7(+" + percent(b.hecatomb()) + ")"));
		lines.add(Component.literal("§7Catacombs Expert Ring: §f" + (b.expertRing() ? "yes" : "no")));
		lines.add(Component.literal("§7Scarf accessory: §f+" + percent(b.scarfBonus()) + " class XP"));
		lines.add(Component.literal("§7Catacombs Graduate: §f" + ROMAN[b.graduateLevel()] + " §7(+" + percent(b.graduate()) + " class XP)"));
		lines.add(Component.literal("§7Catacombs Explorer: §f" + ROMAN[b.explorerLevel()] + " §7(+" + percent(b.explorer()) + " Cata XP)"));
		for (DungeonClass c : DungeonClass.values()) {
			lines.add(Component.literal("§7" + c.displayName() + " perk: §f+" + percent(b.classPerk(c))));
		}
		lines.add(Component.literal("§7Mayor: §f" + (lookup.mayor() == null ? "unknown" : lookup.mayor())
				+ (b.mayorMultiplier() > 1 ? " (+" + percent(b.mayorMultiplier() - 1) + ")" : "")));
		if (config.dungeonGraduateLevel >= 0 || config.dungeonExplorerLevel >= 0) {
			lines.add(Component.literal("§8Shard levels set in your config."));
		} else {
			lines.add(Component.literal("§8Shard levels wrong? Set dungeonGraduateLevel"));
			lines.add(Component.literal("§8and dungeonExplorerLevel in the config."));
		}
		if (b.globalMultiplier() > 1 || b.extraClassBonus() > 0) {
			lines.add(Component.literal("§7From your config: §f+" + percent(b.globalMultiplier() - 1) + " global, +"
					+ percent(b.extraClassBonus()) + " class"));
		}
		return lines;
	}

	private void drawInventory(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		Inventories inv = profile.member().inventories();
		if (!inv.apiEnabled()) {
			g.text(font, "§7Inventory API is turned off for this player.", left + 8, y, WHITE, false);
			return;
		}
		int armorX = left + 8;
		g.text(font, "§7Armor", armorX, y, WHITE, false);
		drawGrid(g, inv.armor(), armorX, y + 10, 1, mouseX, mouseY);
		int equipX = armorX + Math.max(SLOT, font.width("Armor")) + 10;
		g.text(font, "§7Equip", equipX, y, WHITE, false);
		drawGrid(g, inv.equipment(), equipX, y + 10, 1, mouseX, mouseY);

		// The API lists the hotbar first; show it last, under the main inventory, like the game.
		List<SkyblockItem> items = inv.inventory();
		int gridX = equipX + Math.max(SLOT, font.width("Equip")) + 16;
		g.text(font, "§7Inventory", gridX, y, WHITE, false);
		if (items.size() > 9) {
			drawGrid(g, items.subList(9, items.size()), gridX, y + 10, 9, mouseX, mouseY);
		}
		drawGrid(g, items.subList(0, Math.min(9, items.size())), gridX, y + 10 + 3 * SLOT + 4, 9, mouseX, mouseY);
	}

	/**
	 * Wardrobe pages laid out like the game (a column per set: helmet, chestplate, leggings,
	 * boots), then one page per loadout.
	 */
	private static List<StoragePage> wardrobePages(MemberData m) {
		List<StoragePage> pages = new ArrayList<>();
		Inventories inv = m.inventories();
		io.github.streetinman.skyblockpv.core.model.Wardrobe wardrobe = inv.wardrobe();
		if (wardrobe != null && !wardrobe.slots().isEmpty()) {
			var slots = wardrobe.slots();
			for (int from = 0, n = 1; from < slots.size(); from += 9, n++) {
				List<SkyblockItem> grid = new ArrayList<>(java.util.Collections.nCopies(36, (SkyblockItem) null));
				for (int c = 0; c < 9 && from + c < slots.size(); c++) {
					var slot = slots.get(from + c);
					SkyblockItem[] pieces = {slot.helmet(), slot.chestplate(), slot.leggings(), slot.boots()};
					// The worn set lives in the armor slots, not the wardrobe.
					if (slot.number() == wardrobe.equippedSlot() && slot.isEmpty() && inv.armor() != null) {
						for (int r = 0; r < 4 && r < inv.armor().size(); r++) pieces[r] = inv.armor().get(r);
					}
					for (int r = 0; r < 4; r++) grid.set(r * 9 + c, pieces[r]);
				}
				String worn = wardrobe.equippedSlot() > from && wardrobe.equippedSlot() <= from + 9
						? " §7(wearing slot " + wardrobe.equippedSlot() + ")" : "";
				pages.add(new StoragePage("Wardrobe page " + n + worn, grid));
			}
		}
		m.loadouts().forEach((name, items) -> pages.add(new StoragePage("Loadout: " + titleCase(name.replace(' ', '_')), items)));
		return List.copyOf(pages);
	}

	private void drawWardrobe(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		if (wardrobePages.isEmpty()) {
			g.text(font, "§7No wardrobe or loadouts visible (inventory API off?).", left + 8, y, WHITE, false);
			return;
		}
		StoragePage current = wardrobePages.get(Math.min(page, wardrobePages.size() - 1));
		g.text(font, "§e" + fit(current.title(), PANEL_WIDTH - 16), left + 8, y, WHITE, false);
		drawGrid(g, current.items(), left + 8, y + 11, 9, mouseX, mouseY);
		if (profile.member().loadouts().isEmpty() && page == 0) {
			g.text(font, "§8No loadouts found in this profile's API data.", left + 8, y + 11 + 4 * SLOT + 6, WHITE, false);
		}
	}

	private void drawStorage(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		if (storagePages.isEmpty()) {
			g.text(font, "§7No ender chest or backpacks visible (inventory API off?).", left + 8, y, WHITE, false);
			return;
		}
		StoragePage current = storagePages.get(Math.min(page, storagePages.size() - 1));
		g.text(font, "§e" + current.title(), left + 8, y, WHITE, false);
		drawGrid(g, current.items(), left + 8, y + 11, 9, mouseX, mouseY);
	}

	private void drawAccessories(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		List<SkyblockItem> bag = profile.member().inventories().accessoryBag();
		if (bag == null) {
			g.text(font, "§7Accessory bag isn't available (inventory API off).", left + 8, y, WHITE, false);
			return;
		}
		int from = page * ACCESSORIES_PER_PAGE;
		drawGrid(g, bag.subList(Math.min(from, bag.size()), Math.min(bag.size(), from + ACCESSORIES_PER_PAGE)), left + 8, y, 9, mouseX, mouseY);
	}

	private void drawTrophyFish(GuiGraphicsExtractor g, int y) {
		TrophyFishing tf = profile.member().trophyFishing();
		g.text(font, "§6" + tf.rankName() + " §7· " + tf.totalCaught() + " caught · "
				+ tf.fishAtLeast(TrophyFish.Tier.DIAMOND) + "/" + TrophyFish.values().length + " at diamond", left + 8, y, WHITE, false);
		g.text(font, "§cBronze §7Silver §6Gold §bDiamond", left + 8, y + 11, WHITE, false);
		y += 24;
		String[] tierColors = {"§c", "§7", "§6", "§b"};
		int columnWidth = (PANEL_WIDTH - 16) / 2;
		int col = 0;
		int rowY = y;
		for (TrophyFish fish : TrophyFish.values()) {
			int x = left + 8 + col * columnWidth;
			TrophyFish.Tier best = tf.bestTier(fish);
			StringBuilder counts = new StringBuilder();
			for (TrophyFish.Tier tier : TrophyFish.Tier.values()) {
				if (counts.length() > 0) counts.append(' ');
				counts.append(tierColors[tier.ordinal()]).append(tf.count(fish, tier));
			}
			// Counts sit flush right in the column; the name gets whatever room is left.
			int countsWidth = font.width(counts.toString());
			int countsX = x + columnWidth - 8 - countsWidth;
			String name = fit(fish.displayName, countsX - x - 4);
			g.text(font, (best == null ? "§8" : tierColors[best.ordinal()]) + name, x, rowY, WHITE, false);
			g.text(font, counts.toString(), countsX, rowY, WHITE, false);
			if (++col == 2) {
				col = 0;
				rowY += 11;
			}
		}
	}

	private void drawGrid(GuiGraphicsExtractor g, List<SkyblockItem> items, int x, int y, int columns, int mouseX, int mouseY) {
		if (items == null) return;
		for (int i = 0; i < items.size(); i++) {
			int sx = x + (i % columns) * SLOT;
			int sy = y + (i / columns) * SLOT;
			g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0xFF373737);
			SkyblockItem item = items.get(i);
			if (item == null) continue;
			ItemStack stack = stacks.get(item);
			g.item(stack, sx + 1, sy + 1);
			g.itemDecorations(font, stack, sx + 1, sy + 1);
			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
				g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
				hoveredStack = stack;
			}
		}
	}

	private void drawBar(GuiGraphicsExtractor g, int x, int y, int width, double progress, boolean maxed) {
		g.fill(x, y, x + width, y + 4, 0xFF333333);
		g.fill(x, y, x + (int) (width * Math.max(0, Math.min(1, progress))), y + 4, maxed ? 0xFFFFAA00 : 0xFF55FF55);
	}

	private void drawLines(GuiGraphicsExtractor g, List<String> lines, int x, int y) {
		for (String line : lines) {
			g.text(font, line, x, y, WHITE, false);
			y += 11;
		}
	}

	/** Panel background with a thin border and an accent line under the header. */
	private void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		g.fill(x, y, x + w, y + h, BACKGROUND);
		g.fill(x, y, x + w, y + 1, ACCENT);
	}

	/** Headline numbers beside the main panel. */
	private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = frameLeft;
		frame(g, x, top, SIDEBAR_WIDTH, PANEL_HEIGHT);
		if (skinWidget != null) SkinPreview.lookAt(skinWidget, mouseX, mouseY);
		if (profile == null) return;
		MemberData m = profile.member();
		int y = top + (skinWidget != null ? MODEL_HEIGHT + 10 : 8);
		List<String> lines = new ArrayList<>();
		lines.add("§bLvl " + (int) m.skyblockLevel());
		lines.add("§6" + (networth != null ? compact((long) networth.total()) : "…") + " §7NW");
		lines.add("§c" + decimal(m.dungeons().catacombsLevel()) + " §7Cata");
		lines.add("§9" + String.format(Locale.ROOT, "%,d", m.power().magicalPower()) + " §7MP");
		lines.add("§e" + decimal(skills().stream().mapToInt(SkillLevel::level).average().orElse(0)) + " §7SA");
		for (String line : lines) {
			g.centeredText(font, line, x + SIDEBAR_WIDTH / 2, y, WHITE);
			y += 11;
		}
	}

	private void drawFloors(GuiGraphicsExtractor g, int y, DungeonData d) {
		int x = left + 8;
		int[] cols = {0, 40, 90, 140, 190, 240};
		String[] heads = {"§7Floor", "§7Runs", "§7PB", "§7PB S", "§7PB S+", "§7Score"};
		g.text(font, "§7Secrets: §f" + String.format(Locale.ROOT, "%,d", d.secrets()) + " §7· §f" + decimal(d.secretsPerRun())
				+ " §7per run · §f" + String.format(Locale.ROOT, "%,d", d.totalRuns()) + " §7runs", x, y, WHITE, false);
		y += 13;
		for (int i = 0; i < cols.length; i++) g.text(font, heads[i], x + cols[i], y, WHITE, false);
		y += 11;
		for (int f = 0; f <= 7; f++) y = floorRow(g, x, y, cols, f == 0 ? "§aEntrance" : "§aF" + f, d.normalFloors().get(f));
		y += 3;
		for (int f = 1; f <= 7; f++) y = floorRow(g, x, y, cols, "§cM" + f, d.masterFloors().get(f));
	}

	private int floorRow(GuiGraphicsExtractor g, int x, int y, int[] cols, String name, io.github.streetinman.skyblockpv.core.dungeons.FloorStats stats) {
		var f = stats == null ? io.github.streetinman.skyblockpv.core.dungeons.FloorStats.NONE : stats;
		String[] cells = {name, (f.completions() == 0 ? "§8" : "§f") + String.format(Locale.ROOT, "%,d", f.completions()),
				pb(f.fastestMs()), pb(f.fastestSMs()), pb(f.fastestSPlusMs()), f.bestScore() == 0 ? "§8—" : "§f" + f.bestScore()};
		for (int i = 0; i < cols.length; i++) g.text(font, cells[i], x + cols[i], y, WHITE, false);
		return y + 10;
	}

	private static String pb(long ms) {
		return ms <= 0 ? "§8—" : "§f" + time(ms);
	}

	private void drawPets(GuiGraphicsExtractor g, int y, int mouseX, int mouseY) {
		if (sortedPets.isEmpty()) {
			g.text(font, "§7No pets (or the API doesn't show them).", left + 8, y, WHITE, false);
			return;
		}
		String[] colours = {"§f", "§a", "§9", "§5", "§6", "§d"};
		int from = page * PETS_PER_PAGE;
		int columnWidth = (PANEL_WIDTH - 16) / 2;
		for (int i = from; i < Math.min(sortedPets.size(), from + PETS_PER_PAGE); i++) {
			var pet = sortedPets.get(i);
			int slot = i - from;
			int x = left + 8 + (slot / (PETS_PER_PAGE / 2)) * columnWidth;
			int rowY = y + (slot % (PETS_PER_PAGE / 2)) * 11;
			int level = (int) io.github.streetinman.skyblockpv.core.model.PetLevels.level(pet);
			String colour = pet.tierIndex() >= 0 ? colours[pet.tierIndex()] : "§f";
			String line = "§7[" + level + "] " + colour + fit(titleCase(pet.type()), columnWidth - 50) + (pet.active() ? " §a●" : "")
					+ (pet.heldItem() != null ? " §8✦" : "");
			g.text(font, line, x, rowY, WHITE, false);
			if (mouseX >= x && mouseX < x + columnWidth && mouseY >= rowY && mouseY < rowY + 10) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(colour + titleCase(pet.type()) + " §7(" + titleCase(pet.tier()) + ")"));
				tip.add(Component.literal("§7Level: §f" + decimal(io.github.streetinman.skyblockpv.core.model.PetLevels.level(pet))
						+ " §8/ " + io.github.streetinman.skyblockpv.core.model.PetLevels.maxLevel(pet.type())));
				tip.add(Component.literal("§7XP: §f" + String.format(Locale.ROOT, "%,.0f", pet.exp())));
				if (pet.heldItem() != null) tip.add(Component.literal("§7Held item: §f" + titleCase(pet.heldItem().replace("PET_ITEM_", ""))));
				if (pet.skin() != null) tip.add(Component.literal("§7Skin: §f" + titleCase(pet.skin())));
				if (pet.active()) tip.add(Component.literal("§aActive pet"));
				hoveredText = tip;
			}
		}
	}

	private void drawMisc(GuiGraphicsExtractor g, int y) {
		var e = profile.member().extras();
		List<String> general = new ArrayList<>();
		general.add("§eGeneral");
		general.add("§7Joined: §f" + (e.firstJoinMs() <= 0 ? "—" : java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)
				.format(java.time.Instant.ofEpochMilli(e.firstJoinMs()).atZone(java.time.ZoneId.systemDefault()))));
		general.add("§7Profile: §f" + (profile.gameMode() == null ? "Normal" : titleCase(profile.gameMode())) + " §8(" + e.coopMembers()
				+ (e.coopMembers() == 1 ? " player)" : " players)"));
		general.add("§7Fairy Souls: §f" + profile.member().fairySouls());
		general.add("§7Kills: §f" + num(e.kills()));
		general.add("§7Deaths: §f" + num(e.deaths()));
		general.add("§7Highest Crit: §f" + num((long) e.highestCritDamage()));
		general.add("§7Items Fished: §f" + num(e.itemsFished()));
		general.add("§7Gifts: §f" + num(e.giftsGiven()) + " §7given, §f" + num(e.giftsReceived()) + " §7got");
		general.add("§7Minions Crafted: §f" + e.craftedMinions());
		general.add("§7Motes: §d" + num(e.motes()));
		general.add("§7Pets: §f" + profile.member().pets().size());
		general.add("§7Trophy Fish: §f" + profile.member().trophyFishing().totalCaught());
		drawLines(g, general.stream().map(line -> fit(line, 178)).toList(), left + 8, y);

		List<String> other = new ArrayList<>();
		other.add("§bMining");
		other.add("§7HotM: §f" + e.hotmTier() + " §8(" + compact((long) e.hotmXp()) + " XP)");
		other.add("§7Powder: §2" + compact(e.mithrilPowder()) + " §d" + compact(e.gemstonePowder()) + " §b" + compact(e.glacitePowder()));
		other.add("§cCrimson Isle");
		other.add("§7Faction: §f" + (e.crimsonFaction() == null ? "none" : titleCase(e.crimsonFaction())));
		other.add("§7Reputation: §5" + compact(e.mageReputation()) + " §7mage, §c" + compact(e.barbarianReputation()) + " §7barb");
		StringBuilder kuudra = new StringBuilder("§7Kuudra: §f" + e.kuudraTotal());
		String[] tiers = {"none", "hot", "burning", "fiery", "infernal"};
		String[] short_ = {"B", "H", "Bu", "F", "I"};
		other.add(kuudra.toString());
		StringBuilder kuudraTiers = new StringBuilder("§8");
		for (int i = 0; i < tiers.length; i++) kuudraTiers.append(i == 0 ? "" : " ").append(short_[i]).append(e.kuudraCompletions().getOrDefault(tiers[i], 0));
		other.add(kuudraTiers.toString());
		other.add("§aFarming");
		other.add("§7Medals: §6" + e.jacobMedals().getOrDefault("gold", 0) + "g §f" + e.jacobMedals().getOrDefault("silver", 0)
				+ "s §c" + e.jacobMedals().getOrDefault("bronze", 0) + "b");
		other.add("§7Contests: §f" + e.jacobContests());
		other.add("§7Farming cap: §f+" + e.jacobFarmingLevelCap());
		other.add("§7Double Drops: §f" + e.jacobDoubleDrops() + "/15");
		drawLines(g, other.stream().map(line -> fit(line, PANEL_WIDTH - 196)).toList(), left + 190, y);
	}

	private static String num(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	/** Width of the widest line, ignoring § colour codes (Font.width already skips them). */
	private int widest(List<String> lines) {
		return lines.stream().mapToInt(font::width).max().orElse(0);
	}

	/** Shortens text with an ellipsis until it fits in {@code maxWidth} pixels. */
	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) return text;
		String shortened = text;
		while (!shortened.isEmpty() && font.width(shortened + "…") > maxWidth) {
			shortened = shortened.substring(0, shortened.length() - 1);
		}
		return shortened + "…";
	}

	/** Widgets inside the panel are drawn after it, so the panel background doesn't cover them. */
	private <T extends net.minecraft.client.gui.components.AbstractWidget> T panelWidget(T widget) {
		panelWidgets.add(widget);
		return addWidget(widget);
	}

	private int pageCount() {
		if (profile == null) return 1;
		Inventories inv = profile.member().inventories();
		return switch (tab) {
			case STORAGE -> Math.max(1, storagePages.size());
			case WARDROBE -> Math.max(1, wardrobePages.size());
			case DUNGEONS -> 2;
			case PETS -> Math.max(1, (sortedPets.size() + PETS_PER_PAGE - 1) / PETS_PER_PAGE);
			case ACCESSORIES -> inv.accessoryBag() == null ? 1
					: Math.max(1, (inv.accessoryBag().size() + ACCESSORIES_PER_PAGE - 1) / ACCESSORIES_PER_PAGE);
			default -> 1;
		};
	}

	private List<SkillLevel> skills() {
		List<SkillLevel> levels = new ArrayList<>();
		for (Map.Entry<String, Double> e : profile.member().skillXp().entrySet()) {
			if (lookup.skills().knows(e.getKey())) levels.add(lookup.skills().level(e.getKey(), e.getValue()));
		}
		return levels;
	}

	private static String coins(double amount) {
		return String.format(Locale.ROOT, "%,.0f", amount);
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String percent(double fraction) {
		return String.format(Locale.ROOT, "%.4g", fraction * 100).replaceAll("\\.?0+$", "") + "%";
	}

	private static String compact(long value) {
		if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
		if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
		if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
		return Long.toString(value);
	}

	private static String time(long millis) {
		long seconds = millis / 1000;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
