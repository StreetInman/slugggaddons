package io.github.streetinman.skyblockpv.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import io.github.streetinman.skyblockpv.render.ScaledRenderState;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements ScaledRenderState {
	@Unique
	private float[] skyblockpv$axisScale;

	@Override
	public float[] skyblockpv$axisScale() {
		return skyblockpv$axisScale;
	}

	@Override
	public void skyblockpv$setAxisScale(float[] scale) {
		skyblockpv$axisScale = scale;
	}
}
