package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.meow.compile.Linker;
import org.jetbrains.annotations.NotNull;

public record NumberExpression(float value) implements Expression {
	public static final NumberExpression ONE = new NumberExpression(1.0F);
	public static final NumberExpression ZERO = new NumberExpression(0.0F);

	@Override
	public @NotNull String toString() {
		return "Number(" + value + ")";
	}

	@Override
	public boolean constant(Linker linker) {
		return true;
	}
}
