package dev.spiritstudios.mojank.token;

import java.lang.constant.ConstantDesc;

public record ConstantToken(ConstantDesc value) implements MolangToken {
	public static final ConstantToken ZERO = new ConstantToken(0F);
	public static final ConstantToken ONE = new ConstantToken(1F);
	public static final ConstantToken THREE = new ConstantToken(3F);
	public static final ConstantToken FOUR = new ConstantToken(4F);

	public static final ConstantToken TRUE = new ConstantToken(1);
	public static final ConstantToken FALSE = new ConstantToken(0);
}
