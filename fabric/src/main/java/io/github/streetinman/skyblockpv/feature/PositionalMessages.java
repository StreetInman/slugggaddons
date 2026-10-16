package io.github.streetinman.skyblockpv.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.config.PvConfig;

/**
 * Positional messages (like Odin's old feature): save a spot with a radius and a party message.
 * Walking into the spot puts a clickable line in chat; clicking it fills the chat box with
 * {@code /pc <message>} and you press Enter. Nothing is ever sent for you, so it stays within
 * Hypixel's rules on automated chat.
 *
 * <p>{@code /posmsg add <name> <radius> <message>} saves where you stand;
 * {@code /posmsg addat <name> <x> <y> <z> <radius> <message>} saves exact coordinates;
 * {@code /posmsg list}, {@code /posmsg remove <name>}, {@code /posmsg send <name>}.
 * Messages can use {x} {y} {z} (your block position) and {name} (your name).
 */
public final class PositionalMessages {
	private static final Set<String> inside = new HashSet<>();

	private PositionalMessages() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PositionalMessages::tick);
	}

	public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		for (String root : new String[] {"posmsg", "pm"}) {
			LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(root)
					.executes(ctx -> list(ctx.getSource()))
					.then(ClientCommands.literal("list").executes(ctx -> list(ctx.getSource())))
					.then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word())
							.then(ClientCommands.argument("radius", DoubleArgumentType.doubleArg(0.5, 100))
									.then(ClientCommands.argument("message", StringArgumentType.greedyString())
											.executes(PositionalMessages::addHere)))))
					.then(ClientCommands.literal("addat").then(ClientCommands.argument("name", StringArgumentType.word())
							.then(ClientCommands.argument("x", DoubleArgumentType.doubleArg())
									.then(ClientCommands.argument("y", DoubleArgumentType.doubleArg())
											.then(ClientCommands.argument("z", DoubleArgumentType.doubleArg())
													.then(ClientCommands.argument("radius", DoubleArgumentType.doubleArg(0.5, 100))
															.then(ClientCommands.argument("message", StringArgumentType.greedyString())
																	.executes(PositionalMessages::addAt))))))))
					.then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word())
							.executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
					.then(ClientCommands.literal("send").then(ClientCommands.argument("name", StringArgumentType.word())
							.executes(ctx -> send(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
					.then(ClientCommands.literal("toggle").executes(ctx -> toggle(ctx.getSource())));
			dispatcher.register(command);
		}
	}

	private static List<PvConfig.PositionalMessage> messages() {
		return SkyblockPvClient.config().positionalMessages;
	}

	private static int addHere(CommandContext<FabricClientCommandSource> ctx) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return 0;
		return add(ctx, Math.floor(player.getX()) + 0.5, Math.floor(player.getY()), Math.floor(player.getZ()) + 0.5);
	}

	private static int addAt(CommandContext<FabricClientCommandSource> ctx) {
		return add(ctx, DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"));
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, double x, double y, double z) {
		String name = StringArgumentType.getString(ctx, "name");
		PvConfig.PositionalMessage message = new PvConfig.PositionalMessage();
		message.name = name;
		message.x = x;
		message.y = y;
		message.z = z;
		message.radius = DoubleArgumentType.getDouble(ctx, "radius");
		message.message = StringArgumentType.getString(ctx, "message");
		messages().removeIf(m -> m.name.equalsIgnoreCase(name));
		messages().add(message);
		SkyblockPvClient.saveConfig();
		ctx.getSource().sendFeedback(prefix().append(Component.literal("Saved " + name + " at " + coords(message) + ", radius "
				+ fmt(message.radius) + ".").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int remove(FabricClientCommandSource source, String name) {
		boolean removed = messages().removeIf(m -> m.name.equalsIgnoreCase(name));
		SkyblockPvClient.saveConfig();
		source.sendFeedback(prefix().append(Component.literal(removed ? "Removed " + name + "." : "No message called " + name + ".")
				.withStyle(ChatFormatting.GRAY)));
		return removed ? 1 : 0;
	}

	private static int toggle(FabricClientCommandSource source) {
		PvConfig config = SkyblockPvClient.config();
		config.positionalMessagesEnabled = !config.positionalMessagesEnabled;
		SkyblockPvClient.saveConfig();
		source.sendFeedback(prefix().append(Component.literal("Positional messages " + (config.positionalMessagesEnabled ? "on." : "off."))
				.withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int list(FabricClientCommandSource source) {
		List<PvConfig.PositionalMessage> all = messages();
		if (all.isEmpty()) {
			source.sendFeedback(prefix().append(Component.literal("No positional messages. Stand somewhere and use /posmsg add <name> <radius> <message>.")
					.withStyle(ChatFormatting.GRAY)));
			return 1;
		}
		source.sendFeedback(prefix().append(Component.literal(all.size() + " positional message" + (all.size() == 1 ? "" : "s")
				+ (SkyblockPvClient.config().positionalMessagesEnabled ? "" : " (turned off)") + ":").withStyle(ChatFormatting.GRAY)));
		for (PvConfig.PositionalMessage m : all) {
			source.sendFeedback(Component.literal(" " + m.name).withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(" " + coords(m) + " r" + fmt(m.radius) + ": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(m.message).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" [send]").withStyle(sendStyle(fill(m.message)))));
		}
		return 1;
	}

	private static int send(FabricClientCommandSource source, String name) {
		for (PvConfig.PositionalMessage m : messages()) {
			if (m.name.equalsIgnoreCase(name)) {
				openChat("/pc " + fill(m.message));
				return 1;
			}
		}
		source.sendError(Component.literal("No positional message called " + name));
		return 0;
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !SkyblockPvClient.config().positionalMessagesEnabled) {
			inside.clear();
			return;
		}
		for (PvConfig.PositionalMessage m : messages()) {
			double dx = player.getX() - m.x;
			double dy = player.getY() - m.y;
			double dz = player.getZ() - m.z;
			boolean in = dx * dx + dy * dy + dz * dz <= m.radius * m.radius;
			if (in && inside.add(m.name)) {
				String text = fill(m.message);
				player.displayClientMessage(prefix().append(Component.literal(m.name + ": ").withStyle(ChatFormatting.YELLOW))
						.append(Component.literal(text).withStyle(ChatFormatting.WHITE))
						.append(Component.literal(" [Click to send to party]").withStyle(sendStyle(text))), false);
				if (SkyblockPvClient.config().positionalMessagesOpenChat) openChat("/pc " + text);
			} else if (!in) {
				inside.remove(m.name);
			}
		}
	}

	private static Style sendStyle(String text) {
		return Style.EMPTY.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.SuggestCommand("/pc " + text))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Puts /pc " + text + " in the chat box; press Enter to send")));
	}

	/** Opens chat with the text typed in, ready for the player to send. */
	private static void openChat(String text) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> io.github.streetinman.skyblockpv.compat.Compat.setScreen(new net.minecraft.client.gui.screens.ChatScreen(text, false)));
	}

	private static String fill(String message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return message;
		return message.replace("{x}", Integer.toString(player.getBlockX()))
				.replace("{y}", Integer.toString(player.getBlockY()))
				.replace("{z}", Integer.toString(player.getBlockZ()))
				.replace("{name}", player.getName().getString());
	}

	private static MutableComponent prefix() {
		return Component.literal("[SlugggAddons] ").withStyle(ChatFormatting.GOLD);
	}

	private static String coords(PvConfig.PositionalMessage m) {
		return fmt(m.x) + " " + fmt(m.y) + " " + fmt(m.z);
	}

	private static String fmt(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	static List<String> names() {
		List<String> names = new ArrayList<>();
		for (PvConfig.PositionalMessage m : messages()) names.add(m.name);
		return names;
	}
}
