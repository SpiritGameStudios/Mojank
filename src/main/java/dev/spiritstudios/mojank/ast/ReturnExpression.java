package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record ReturnExpression(Expression value) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Return(");
		value.append(builder);
		builder.append(")");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
