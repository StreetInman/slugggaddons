package io.github.streetinman.skyblockpv.gui;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.world.entity.player.PlayerSkin;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.compat.Compat;
import io.github.streetinman.skyblockpv.mixin.PlayerSkinWidgetAccessor;

/**
 * The viewed player's skin for the /pv sidebar, drawn with the same widget as the game's skin
 * customisation screen. Unlike a fake player entity this needs no world, and every skin manager
 * call happens on the render thread; only the profile lookup (a plain web request) runs in the
 * background.
 */
final class SkinPreview {
	private volatile Supplier<PlayerSkin> skin;

	SkinPreview(String undashedUuid, String name) {
		Minecraft mc = Minecraft.getInstance();
		UUID uuid = UUID.fromString(undashedUuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
		// Default (Steve/Alex) skin until the real one arrives.
		skin = mc.getSkinManager().createLookup(new GameProfile(uuid, name), false);
		CompletableFuture.supplyAsync(() -> Compat.fetchProfile(uuid)).whenComplete((full, error) -> {
			if (error != null || full == null) {
				SkyblockPvClient.LOGGER.debug("Couldn't load the profile for {}'s skin", name, error);
				return;
			}
			mc.execute(() -> skin = mc.getSkinManager().createLookup(full, false));
		});
	}

	PlayerSkinWidget widget(int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		return new PlayerSkinWidget(width, height, mc.getEntityModels(), () -> skin.get());
	}

	/** Turns the model towards the mouse, like the inventory player does. */
	static void lookAt(PlayerSkinWidget widget, int mouseX, int mouseY) {
		float dx = mouseX - (widget.getX() + widget.getWidth() / 2f);
		float dy = mouseY - (widget.getY() + widget.getHeight() / 3f);
		PlayerSkinWidgetAccessor access = (PlayerSkinWidgetAccessor) widget;
		access.skyblockpv$setRotationY(Math.clamp(-dx * 0.35f, -70f, 70f));
		access.skyblockpv$setRotationX(Math.clamp(-dy * 0.2f, -30f, 30f));
	}
}
