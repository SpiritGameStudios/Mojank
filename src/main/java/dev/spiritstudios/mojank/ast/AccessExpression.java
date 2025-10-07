package dev.spiritstudios.mojank.ast;

public record AccessExpression(Expression object, String toAccess) implements Expression {

}
