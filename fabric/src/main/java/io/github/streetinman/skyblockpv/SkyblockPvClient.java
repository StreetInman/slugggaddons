package io.github.streetinman.skyblockpv;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import io.github.streetinman.skyblockpv.compat.Compat;
import io.github.streetinman.skyblockpv.config.PvConfig;
import io.github.streetinman.skyblockpv.core.api.HypixelClient;
import io.github.streetinman.skyblockpv.core.api.MojangClient;
import io.github.streetinman.skyblockpv.core.api.ProfileService;
import io.github.streetinman.skyblockpv.core.networth.PriceClient;
import io.github.streetinman.skyblockpv.feature.PositionalMessages;
import io.github.streetinman.skyblockpv.gui.PvScreen;
import io.github.streetinman.skyblockpv.gui.ItemBrowserScreen;
import io.github.streetinman.skyblockpv.gui.SettingsScreen;

/**
 * SlugggAddons. Registers {@code /pv [player]} (also {@code /spv} and {@code /sbpv}),
 * {@code /slugggaddons} for settings, {@code /sbitems}, {@code /posmsg} and {@code /pvdump <player>}.
 *
 * <p>Everything here is read-only: the mod calls the public Hypixel API over HTTPS and draws its
 * own screen. It never sends packets to the server or acts on the player's behalf.
 */
public final class SkyblockPvClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("slugggaddons");

	private static final String[] COMMAND_NAMES = {"pv", "spv", "sbpv"};
	private static final String[] ITEM_COMMAND_NAMES = {"sbitems", "sbrecipe"};

	private static PvConfig config;
	private static Path configFile;
	private static ProfileService profiles;
	private static PriceClient prices;

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		configFile = configDir.resolve("slugggaddons.json");
		migrateOldFiles(configDir);
		config = PvConfig.load(configFile);

		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HypixelClient hypixel = new HypixelClient(http, () -> config.apiBaseUrl, () -> config.apiKey);
		profiles = new ProfileService(new MojangClient(http), hypixel);
		prices = new PriceClient(http, hypixel, () -> config.lowestBinUrl);

		PositionalMessages.register();
		if ("1".equals(System.getenv("SKYBLOCKPV_SELFTEST"))) SelfTest.start();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// Other SkyBlock mods often claim /pv too, so the same command is also on /spv and /sbpv.
			for (String name : COMMAND_NAMES) {
				dispatcher.register(ClientCommands.literal(name)
						.executes(ctx -> open(Minecraft.getInstance().getUser().getName()))
						.then(ClientCommands.argument("player", StringArgumentType.word())
								.executes(ctx -> open(StringArgumentType.getString(ctx, "player")))));
			}
			// Settings: /slugggaddons, with the old /skyblockpv kept as an alias.
			for (String name : new String[] {"slugggaddons", "skyblockpv"}) {
				dispatcher.register(ClientCommands.literal(name).executes(ctx -> {
					Minecraft mc = Minecraft.getInstance();
					mc.schedule(() -> Compat.setScreen(new SettingsScreen(config, SkyblockPvClient::saveConfig)));
					return 1;
				}).then(ClientCommands.literal("items").executes(ctx -> openItems(""))));
			}
			PositionalMessages.registerCommands(dispatcher);
			for (String name : ITEM_COMMAND_NAMES) {
				dispatcher.register(ClientCommands.literal(name)
						.executes(ctx -> openItems(""))
						.then(ClientCommands.argument("search", StringArgumentType.greedyString())
								.executes(ctx -> openItems(StringArgumentType.getString(ctx, "search")))));
			}
			dispatcher.register(ClientCommands.literal("pvdump")
					.then(ClientCommands.argument("player", StringArgumentType.word())
							.executes(SkyblockPvClient::dump)));
		});
	}

	/** Before the rename to SlugggAddons the config and data lived under "skyblock-pv". */
	private static void migrateOldFiles(Path configDir) {
		try {
			Path oldConfig = configDir.resolve("skyblock-pv.json");
			if (!Files.exists(configFile) && Files.exists(oldConfig)) Files.copy(oldConfig, configFile);
			Path oldDir = configDir.resolve("skyblock-pv");
			Path newDir = configDir.resolve("slugggaddons");
			if (!Files.exists(newDir) && Files.isDirectory(oldDir)) Files.move(oldDir, newDir);
		} catch (java.io.IOException e) {
			LOGGER.warn("Couldn't move old skyblock-pv settings", e);
		}
	}

	public static int open(String player) {
		Minecraft mc = Minecraft.getInstance();
		// Open next tick: the chat screen closes after the command runs and would replace ours.
		mc.schedule(() -> Compat.setScreen(new PvScreen(player, profiles, config)));
		return 1;
	}

	public static int openItems(String search) {
		Minecraft.getInstance().schedule(() -> Compat.setScreen(new ItemBrowserScreen(null, search)));
		return 1;
	}

	/** Saves the raw API response for a player, to inspect new fields such as loadouts. */
	private static int dump(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player");
		FabricClientCommandSource source = ctx.getSource();
		profiles.lookup(player).whenComplete((lookup, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				source.sendError(Component.literal("pvdump failed: " + rootMessage(error)));
				return;
			}
			try {
				Path file = FabricLoader.getInstance().getConfigDir().resolve("slugggaddons/dumps/" + lookup.player().name() + ".json");
				Files.createDirectories(file.getParent());
				Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(lookup.raw()));
				source.sendFeedback(Component.literal("Saved API response to " + file));
			} catch (Exception e) {
				source.sendError(Component.literal("Couldn't write dump: " + e.getMessage()));
			}
		}));
		return 1;
	}

	/** Live settings, read every frame by the render mixins. */
	public static PvConfig config() {
		return config;
	}

	public static void saveConfig() {
		config.save(configFile);
	}

	public static ProfileService profiles() {
		return profiles;
	}

	public static PriceClient prices() {
		return prices;
	}

	public static String rootMessage(Throwable error) {
		Throwable t = error;
		while (t.getCause() != null && (t.getMessage() == null || t instanceof java.util.concurrent.CompletionException)) {
			t = t.getCause();
		}
		return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
	}
}
