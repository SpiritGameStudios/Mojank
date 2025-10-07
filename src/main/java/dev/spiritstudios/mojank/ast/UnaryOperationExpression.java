package dev.spiritstudios.mojank.ast;

public record UnaryOperationExpression(Expression value, Operator operator) implements Expression {
	public enum	Operator {
		NEGATE,
		LOGICAL_NEGATE
	}
}
