package dev.spiritstudios.mojank.ast;

public record ArrayAccessExpression(Expression array, Expression toAccess) implements Expression {

}
