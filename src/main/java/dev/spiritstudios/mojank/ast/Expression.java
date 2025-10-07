package dev.spiritstudios.mojank.ast;

public sealed interface Expression permits AccessExpression, ArrayAccessExpression, BinaryOperationExpression,
	BreakExpression, ComplexExpression, ContinueExpression, FunctionCallExpression, IdentifierExpression,
	NumberExpression, ReturnExpression, StringExpression, TernaryOperationExpression, UnaryOperationExpression {

	default void append(IndentedStringBuilder builder) {
		builder.append(this.toString());
	}
}

