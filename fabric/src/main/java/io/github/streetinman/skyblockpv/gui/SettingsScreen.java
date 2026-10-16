package io.github.streetinman.skyblockpv.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.streetinman.skyblockpv.config.PvConfig;

/**
 * {@code /skyblockpv}: the mod's settings, one tab per area of the game. Every option here is
 * client-side; changes apply immediately and are saved when the screen closes. New tweaks go in
 * the tab for the part of the game they affect.
 */
public class SettingsScreen extends Screen {
	private static final int PANEL_WIDTH = 330;
	private static final int PANEL_HEIGHT = 200;
	private static final int COLUMN = 155;
	private static final int ROW = 22;
	private static final int WHITE = 0xFFFFFFFF;

	private enum Category {
		GENERAL("General"), GUI("GUI"), PLAYER("Player"), ANIMATIONS("Animations"), DUNGEONS("Dungeons"),
		GARDEN("Garden"), MINING("Mining"), FISHING("Fishing");

		final String label;

		Category(String label) {
			this.label = label;
		}
	}

	private static Category lastCategory = Category.GENERAL;

	private final PvConfig config;
	private final Runnable save;
	private Category category = lastCategory;
	private int left;
	private int top;
	private int col1;
	private int col2;

	/** Shows the n-th category tab. Used by the launch self-test. */
	public void showCategory(int index) {
		category = Category.values()[Math.floorMod(index, Category.values().length)];
		rebuildWidgets();
	}

	public static int categoryCount() {
		return Category.values().length;
	}

	public SettingsScreen(PvConfig config, Runnable save) {
		super(Component.literal("SlugggAddons Settings"));
		this.config = config;
		this.save = save;
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(38, (height - PANEL_HEIGHT) / 2 + 10);
		col1 = left + 6;
		col2 = left + PANEL_WIDTH - 6 - COLUMN;

		// Category tabs share the panel width.
		Category[] all = Category.values();
		int gap = 2;
		int x = left;
		int each = (PANEL_WIDTH - gap * (all.length - 1)) / all.length;
		for (int i = 0; i < all.length; i++) {
			Category c = all[i];
			int w = i == all.length - 1 ? left + PANEL_WIDTH - x : each;
			addRenderableWidget(Button.builder(Component.literal(c.label), b -> {
				category = c;
				lastCategory = c;
				rebuildWidgets();
			}).bounds(x, top - 22, w, 20).build()).active = c != category;
			x += w + gap;
		}

		int y = top + 22;
		switch (category) {
			case GENERAL -> {
				addRenderableWidget(Button.builder(Component.literal("Open item browser"), b -> io.github.streetinman.skyblockpv.compat.Compat.setScreen(
						new ItemBrowserScreen(this, ""))).bounds(col1, y, COLUMN, 20).build());
			}
			case GUI -> {
				PvConfig.Gui gui = config.gui;
				addRenderableWidget(toggle(col1, y, "Model + summary in /pv", () -> gui.showSidebar, () -> gui.showSidebar = !gui.showSidebar));
				List<String> tabs = PvScreen.tabLabels();
				addRenderableWidget(Button.builder(Component.literal("/pv opens on: " + gui.defaultTab), b -> {
					int i = tabs.indexOf(gui.defaultTab);
					gui.defaultTab = tabs.get((i + 1) % tabs.size());
					b.setMessage(Component.literal("/pv opens on: " + gui.defaultTab));
				}).bounds(col2, y, COLUMN, 20).build());
			}
			case PLAYER -> {
				PvConfig.PlayerModel model = config.playerModel;
				addRenderableWidget(new Slider(col1, y, "Width (X)", -3.0, 3.0, 0.05, () -> model.scaleX, v -> model.scaleX = v, times()));
				addRenderableWidget(new Slider(col1, y + ROW, "Height (Y)", -3.0, 3.0, 0.05, () -> model.scaleY, v -> model.scaleY = v, times()));
				addRenderableWidget(new Slider(col1, y + 2 * ROW, "Depth (Z)", -3.0, 3.0, 0.05, () -> model.scaleZ, v -> model.scaleZ = v, times()));
				addRenderableWidget(toggle(col2, y, "Other players too", () -> model.includeOtherPlayers,
						() -> model.includeOtherPlayers = !model.includeOtherPlayers));
				addRenderableWidget(Button.builder(Component.literal("Reset size"), b -> {
					model.reset();
					rebuildWidgets();
				}).bounds(col2, y + ROW, COLUMN, 20).build());
			}
			case ANIMATIONS -> {
				PvConfig.ItemAnimations anim = config.itemAnimations;
				addRenderableWidget(toggle(col1, y, "Item animations", () -> anim.enabled, () -> anim.enabled = !anim.enabled));
				addRenderableWidget(new Slider(col1, y + ROW, "Item size", 0.25, 2.0, 0.05, () -> anim.scale, v -> anim.scale = v, times()));
				addRenderableWidget(new Slider(col1, y + 2 * ROW, "Swing speed", 0.25, 3.0, 0.05, () -> anim.swingSpeed, v -> anim.swingSpeed = v, times()));
				addRenderableWidget(toggle(col1, y + 3 * ROW, "Skip equip animation", () -> anim.noEquipAnimation,
						() -> anim.noEquipAnimation = !anim.noEquipAnimation));
				addRenderableWidget(Button.builder(Component.literal("Reset animations"), b -> {
					anim.reset();
					rebuildWidgets();
				}).bounds(col1, y + 5 * ROW, COLUMN, 20).build());
				addRenderableWidget(new Slider(col2, y, "Offset X", -1.5, 1.5, 0.05, () -> anim.offsetX, v -> anim.offsetX = v, fixed(2)));
				addRenderableWidget(new Slider(col2, y + ROW, "Offset Y", -1.5, 1.5, 0.05, () -> anim.offsetY, v -> anim.offsetY = v, fixed(2)));
				addRenderableWidget(new Slider(col2, y + 2 * ROW, "Offset Z", -1.5, 1.5, 0.05, () -> anim.offsetZ, v -> anim.offsetZ = v, fixed(2)));
				addRenderableWidget(new Slider(col2, y + 3 * ROW, "Rotate X", -180, 180, 5, () -> anim.rotationX, v -> anim.rotationX = v, degrees()));
				addRenderableWidget(new Slider(col2, y + 4 * ROW, "Rotate Y", -180, 180, 5, () -> anim.rotationY, v -> anim.rotationY = v, degrees()));
				addRenderableWidget(new Slider(col2, y + 5 * ROW, "Rotate Z", -180, 180, 5, () -> anim.rotationZ, v -> anim.rotationZ = v, degrees()));
			}
			case DUNGEONS -> {
				addRenderableWidget(new Slider(col1, y, "Team share", 0.1, 0.5, 0.05, () -> config.dungeonTeamShare,
						v -> config.dungeonTeamShare = v, v -> String.format(Locale.ROOT, "%.0f%%", v * 100)));
				addRenderableWidget(new Slider(col1, y + ROW, "Global boost", 0, 100, 5, () -> config.dungeonGlobalBoostPercent,
						v -> config.dungeonGlobalBoostPercent = v, v -> String.format(Locale.ROOT, "+%.0f%%", v)));
				addRenderableWidget(new Slider(col1, y + 2 * ROW, "Extra class XP", 0, 50, 1, () -> config.dungeonExtraClassBoostPercent,
						v -> config.dungeonExtraClassBoostPercent = v, v -> String.format(Locale.ROOT, "+%.0f%%", v)));
				addRenderableWidget(new Slider(col2, y, "Graduate", -1, 10, 1, () -> config.dungeonGraduateLevel,
						v -> config.dungeonGraduateLevel = (int) v, autoOrLevel()));
				addRenderableWidget(new Slider(col2, y + ROW, "Explorer", -1, 10, 1, () -> config.dungeonExplorerLevel,
						v -> config.dungeonExplorerLevel = (int) v, autoOrLevel()));
				addRenderableWidget(toggle(col2, y + 2 * ROW, "Positional msgs", () -> config.positionalMessagesEnabled,
						() -> config.positionalMessagesEnabled = !config.positionalMessagesEnabled));
				addRenderableWidget(toggle(col2, y + 3 * ROW, "Auto-send msgs", () -> config.positionalMessagesAutoSend,
						() -> config.positionalMessagesAutoSend = !config.positionalMessagesAutoSend));
			}
			default -> {
			}
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 75, top + PANEL_HEIGHT + 6, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF3A3A5C);
		g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101018);
		g.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF6C6CFF);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, "§6§lSlugggAddons", width / 2, top - 36, WHITE);
		g.text(font, "§e" + category.label, col1, top + 8, WHITE, true);
		int y = top + PANEL_HEIGHT - 50;
		for (String line : notes()) {
			g.centeredText(font, line, left + PANEL_WIDTH / 2, y, WHITE);
			y += 11;
		}
	}

	/** Short help under each category's options. */
	private List<String> notes() {
		return switch (category) {
			case GENERAL -> List.of("§7/pv <player> (/spv, /sbpv) · /sbitems · /posmsg",
					"§7Every setting is client-side and changes only", "§7what you see. Nothing is sent to the server.");
			case GUI -> List.of("§7How the /pv window looks.");
			case PLAYER -> List.of("§7Stretch the drawn model on each axis. Negative", "§7height turns it upside down. Your hitbox and",
					"§7movement don't change; only you see it.");
			case ANIMATIONS -> List.of("§7First-person item position, size and swing.", "§726.3: swing speed can only be faster.");
			case DUNGEONS -> List.of("§7M7 calculator: Auto reads Graduate/Explorer from shards.",
					"§7Positional messages: /posmsg add <name> <radius> <msg>.", "§7Auto-send off: click the chat line to send instead.");
			case GARDEN, MINING, FISHING -> List.of("§7No " + category.label.toLowerCase(Locale.ROOT) + " tweaks yet.");
		};
	}

	@Override
	public void removed() {
		save.run();
		super.removed();
	}

	private Button toggle(int x, int y, String label, BooleanSupplier get, Runnable flip) {
		return Button.builder(toggleLabel(label, get.getAsBoolean()), b -> {
			flip.run();
			b.setMessage(toggleLabel(label, get.getAsBoolean()));
		}).bounds(x, y, COLUMN, 20).build();
	}

	private static Component toggleLabel(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
	}

	private static DoubleFunction<String> times() {
		return v -> String.format(Locale.ROOT, "%.2fx", v);
	}

	private static DoubleFunction<String> fixed(int decimals) {
		return v -> String.format(Locale.ROOT, "%." + decimals + "f", v);
	}

	private static DoubleFunction<String> degrees() {
		return v -> String.format(Locale.ROOT, "%.0f°", v);
	}

	private static DoubleFunction<String> autoOrLevel() {
		return v -> v < 0 ? "Auto" : String.format(Locale.ROOT, "%.0f", v);
	}

	/** A slider over [min, max] that snaps to {@code step} and writes straight into the config. */
	private static final class Slider extends AbstractSliderButton {
		private final String label;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer setter;
		private final DoubleFunction<String> format;

		Slider(int x, int y, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter,
				DoubleFunction<String> format) {
			super(x, y, COLUMN, 20, Component.empty(), (Math.clamp(getter.getAsDouble(), min, max) - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.setter = setter;
			this.format = format;
			updateMessage();
		}

		private double current() {
			double raw = min + value * (max - min);
			return Math.clamp(Math.round(raw / step) * step, min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + format.apply(current())));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
		}
	}
}
