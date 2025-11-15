package dev.spiritstudios.mojank.token;

public record NumberToken(float value) implements MolangToken {
	public static final NumberToken ZERO = new NumberToken(0F);
	public static final NumberToken ONE = new NumberToken(1F);
	public static final NumberToken THREE = new NumberToken(3F);
	public static final NumberToken FOUR = new NumberToken(4F);
}
