package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;

public sealed interface Expression permits AccessExpression, ArrayAccessExpression, BinaryOperationExpression,
	BreakExpression, ComplexExpression, ContinueExpression, FunctionCallExpression,
		NumberExpression, ReturnExpression, StringExpression, TernaryOperationExpression, UnaryOperationExpression {

	default void append(IndentedStringBuilder builder) {
		builder.append(this.toString());
	}

	default String toStr() {
		var builder = new IndentedStringBuilder(new StringBuilder());
		append(builder);
		return builder.toString();
	}
}

