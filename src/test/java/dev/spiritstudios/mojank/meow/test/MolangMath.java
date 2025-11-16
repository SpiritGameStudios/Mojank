package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.meow.binding.Pure;

public final class MolangMath {
	public static final float pi = (float) Math.PI;

	@Pure
	public static float sin(float x) {
		return (float) Math.sin(x);
	}

	@Pure
	public static float cos(float x) {
		return (float) Math.cos(x);
	}
}
