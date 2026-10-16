package io.github.streetinman.skyblockpv.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.config.PvConfig;
import io.github.streetinman.skyblockpv.render.ScaledRenderState;

/**
 * Player model size per axis. Only the drawn model is scaled, so the real hitbox, movement and
 * everything the server sees stay the same.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void skyblockpv$rememberScale(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		PvConfig.PlayerModel settings = SkyblockPvClient.config().playerModel;
		boolean applies = entity instanceof Player && !settings.isDefault()
				&& (entity == Minecraft.getInstance().player || settings.includeOtherPlayers);
		((ScaledRenderState) state).skyblockpv$setAxisScale(applies ? settings.axes() : null);
	}

	/**
	 * Runs right after the renderer's own scale step, where the model is still flipped (y points
	 * down) and the feet are at the origin.
	 */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", shift = At.Shift.AFTER,
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void skyblockpv$applyScale(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera,
			CallbackInfo ci) {
		float[] axes = ((ScaledRenderState) state).skyblockpv$axisScale();
		if (axes == null) return;
		float x = axes[0];
		float y = axes[1];
		float z = axes[2];
		// An odd number of flipped axes would turn the model inside out (faces culled from the
		// wrong side). Upside down becomes a half turn instead; other flips turn it to face back.
		if ((x < 0) ^ (y < 0) ^ (z < 0)) {
			if (y < 0) x = -x;
			else z = -z;
		}
		// The model hangs 1.5 blocks "down" from the origin; when flipped, lift it back above the feet.
		if (y < 0) pose.translate(0, 1.501f * y, 0);
		pose.scale(x, y, z);
	}
}
