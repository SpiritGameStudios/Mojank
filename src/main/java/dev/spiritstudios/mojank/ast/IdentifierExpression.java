package dev.spiritstudios.mojank.ast;

import org.jetbrains.annotations.NotNull;

public record IdentifierExpression(String value) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Id(").append(value).append(")");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
