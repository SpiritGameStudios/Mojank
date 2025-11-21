package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.link.Linker;

public sealed interface Expression permits AccessExpression, ArrayAccessExpression, BinaryOperationExpression,
	ComplexExpression, FunctionCallExpression, KeywordExpression, NumberExpression,
	StringExpression, TernaryOperationExpression, UnaryOperationExpression {

	boolean constant(Linker linker);

	default void append(IndentedStringBuilder builder) {
		builder.append(this.toString());
	}

	default String toStr() {
		var builder = new IndentedStringBuilder(new StringBuilder());
		append(builder);
		return builder.toString();
	}
}
