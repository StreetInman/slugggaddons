package io.github.streetinman.skyblockpv.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.PlayerSkinWidget;

/** Lets the /pv model turn towards the mouse; the widget itself only turns when dragged. */
@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
	@Accessor("rotationX")
	void skyblockpv$setRotationX(float value);

	@Accessor("rotationY")
	void skyblockpv$setRotationY(float value);
}
