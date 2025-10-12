package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record NumberExpression(float value) implements Expression {
	public static final NumberExpression ONE = new NumberExpression(1.0F);
	public static final NumberExpression ZERO = new NumberExpression(0.0F);

	@Override
	public @NotNull String toString() {
		return "Number(" + value + ")";
	}
}
