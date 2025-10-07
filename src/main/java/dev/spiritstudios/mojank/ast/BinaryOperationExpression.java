package dev.spiritstudios.mojank.ast;

import org.jetbrains.annotations.NotNull;

public record BinaryOperationExpression(Expression left, Operator operator, Expression right) implements Expression {
	public enum	Operator {
		SET(0),
		NULL_COALESCE(1),
		CONDITIONAL(2),
		LOGICAL_OR(3),
		LOGICAL_AND(4),
		EQUAL_TO(5),
		NOT_EQUAL(5),
		LESS_THAN(6),
		GREATER_THAN(6),
		LESS_THAN_OR_EQUAL_TO(6),
		GREATER_THAN_OR_EQUAL_TO(6),
		ADD(7),
		SUBTRACT(7),
		MULTIPLY(8),
		DIVIDE(8),
		ARROW(999);

		public final int precedence;

		Operator(int precedence) {
			this.precedence = precedence;
		}
	}

	@Override
	public @NotNull String toString() {
			return "(" + left.toString() + ", " + operator.toString() + ", " + right.toString() + ")";
	}
}
