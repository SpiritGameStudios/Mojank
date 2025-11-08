package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record UnaryOperationExpression(Expression value, Operator operator) implements Expression {
	public enum	Operator {
		NEGATE,
		LOGICAL_NEGATE,
		RETURN
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("UnaryOperation[").append(operator.toString()).append(", ");
		value.append(builder);
		builder.append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
