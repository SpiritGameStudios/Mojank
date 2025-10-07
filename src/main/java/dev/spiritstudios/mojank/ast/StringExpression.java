package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record StringExpression(String value) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("String(\"").append(value).append("\")");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
