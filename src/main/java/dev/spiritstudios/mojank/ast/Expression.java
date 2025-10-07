package dev.spiritstudios.mojank.ast;

public sealed interface Expression permits AccessExpression, ArrayAccessExpression, BinaryOperationExpression,
	BreakExpression, CompoundExpression, ContinueExpression, FunctionCallExpression, IdentifierExpression,
	NumberExpression, ReturnExpression, StringExpression, TernaryOperationExpression, UnaryOperationExpression {
}

