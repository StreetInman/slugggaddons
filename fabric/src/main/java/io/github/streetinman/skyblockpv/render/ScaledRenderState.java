package io.github.streetinman.skyblockpv.render;

/** Per-axis player model scale, carried on a render state from extraction to drawing. */
public interface ScaledRenderState {
	float[] skyblockpv$axisScale();

	void skyblockpv$setAxisScale(float[] scale);
}
