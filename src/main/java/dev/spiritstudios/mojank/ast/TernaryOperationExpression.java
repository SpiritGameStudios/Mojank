package dev.spiritstudios.mojank.ast;

public record TernaryOperationExpression(Expression condition, Expression ifTrue, Expression ifFalse) implements Expression {

}
