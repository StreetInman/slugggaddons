package io.github.streetinman.skyblockpv.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.streetinman.skyblockpv.core.api.HypixelClient;

/**
 * Settings stored in {@code config/slugggaddons.json}.
 *
 * <p>{@code apiKey} is only for development: paste your own key from developer.hypixel.net. It
 * stays on your computer. Release builds will point {@code apiBaseUrl} at the project proxy
 * and leave the key empty.
 */
public final class PvConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String apiKey = "";
	public String apiBaseUrl = HypixelClient.HYPIXEL_BASE_URL;
	/** Lowest BIN prices for networth, as a flat {"ITEM_ID": price} JSON object (NEU format). */
	public String lowestBinUrl = "https://sky.coflnet.com/api/prices/neu";
	/** Item and recipe data for /sbitems: the NotEnoughUpdates repository (re-downloaded every 3 days). */
	public String itemRepoUrl = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";

	// Dungeon calculator. Perks, Hecatomb, Scarf, Expert Ring, shard attributes and Derpy are read
	// from the profile automatically; these cover what the API can't see or gets wrong.
	/** An active global dungeon XP boost, e.g. 20 for +20%. */
	public double dungeonGlobalBoostPercent = 0;
	/** Catacombs Graduate shard attribute level (0–10), or -1 to read it from the profile. */
	public int dungeonGraduateLevel = -1;
	/** Catacombs Explorer shard attribute level (0–10), or -1 to read it from the profile. */
	public int dungeonExplorerLevel = -1;
	/** Any other class XP bonus you have that the API doesn't expose. */
	public double dungeonExtraClassBoostPercent = 0;
	/** Share of a run's class XP that the classes you didn't play receive. */
	public double dungeonTeamShare = io.github.streetinman.skyblockpv.core.dungeons.XpBoosts.DEFAULT_TEAM_SHARE;

	/** Positional party messages, see /posmsg. */
	public boolean positionalMessagesEnabled = true;
	/** Also open the chat box with the message typed in when you reach a spot (you still press Enter). */
	public boolean positionalMessagesOpenChat = false;
	public java.util.List<PositionalMessage> positionalMessages = new java.util.ArrayList<>();

	/** How the /pv window looks. */
	public Gui gui = new Gui();
	/** Client-side look of player models. Only changes what you see; nothing is sent to the server. */
	public PlayerModel playerModel = new PlayerModel();
	/** Client-side first-person item position, size and swing animation. */
	public ItemAnimations itemAnimations = new ItemAnimations();

	public static final class PositionalMessage {
		public String name = "";
		public double x;
		public double y;
		public double z;
		public double radius = 3;
		public String message = "";
	}

	public static final class Gui {
		/** Show the player model and summary column beside the /pv window. */
		public boolean showSidebar = true;
		/** Tab /pv opens on, by its label (e.g. "Stats", "Dungeons"). */
		public String defaultTab = "Stats";
	}

	public static final class PlayerModel {
		/** Old single size setting; read once and folded into the three axes. */
		public double scale = 1.0;
		/** Width, height and depth of the drawn model, 1 = normal. Negative flips (upside down for height). */
		public double scaleX = 1.0;
		public double scaleY = 1.0;
		public double scaleZ = 1.0;
		/** Apply the same size to other players too. */
		public boolean includeOtherPlayers = false;

		/** Moves an old uniform size into the per-axis settings. */
		public void migrate() {
			if (scale != 1.0) {
				scaleX *= scale;
				scaleY *= scale;
				scaleZ *= scale;
				scale = 1.0;
			}
		}

		public boolean isDefault() {
			return scaleX == 1.0 && scaleY == 1.0 && scaleZ == 1.0;
		}

		public float[] axes() {
			return new float[] {(float) scaleX, (float) scaleY, (float) scaleZ};
		}

		public void reset() {
			scaleX = scaleY = scaleZ = 1.0;
		}
	}

	public static final class ItemAnimations {
		public boolean enabled = false;
		/** Size of the held item and hand, 1 = normal. */
		public double scale = 1.0;
		/** Offsets in blocks. X is mirrored for the off hand so both move outward together. */
		public double offsetX = 0;
		public double offsetY = 0;
		public double offsetZ = 0;
		/** Rotations in degrees. */
		public double rotationX = 0;
		public double rotationY = 0;
		public double rotationZ = 0;
		/** Swing animation speed, 1 = normal, 2 = twice as fast. Visual only. */
		public double swingSpeed = 1.0;
		/** Skip the dip when switching items. */
		public boolean noEquipAnimation = false;

		public void reset() {
			ItemAnimations defaults = new ItemAnimations();
			boolean wasEnabled = enabled;
			scale = defaults.scale;
			offsetX = offsetY = offsetZ = 0;
			rotationX = rotationY = rotationZ = 0;
			swingSpeed = defaults.swingSpeed;
			noEquipAnimation = false;
			enabled = wasEnabled;
		}
	}

	public static PvConfig load(Path file) {
		try {
			if (Files.exists(file)) {
				PvConfig config = GSON.fromJson(Files.readString(file), PvConfig.class);
				if (config != null) {
					if (config.playerModel != null) config.playerModel.migrate();
					// Re-save so settings added in newer versions show up in the file.
					config.save(file);
					return config;
				}
			}
		} catch (IOException | RuntimeException e) {
			io.github.streetinman.skyblockpv.SkyblockPvClient.LOGGER.warn("Couldn't read {}, using defaults", file, e);
		}
		PvConfig config = new PvConfig();
		config.save(file);
		return config;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this));
		} catch (IOException e) {
			io.github.streetinman.skyblockpv.SkyblockPvClient.LOGGER.warn("Couldn't save {}", file, e);
		}
	}
}
