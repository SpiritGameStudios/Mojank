package dev.spiritstudios.mojank.ast;

import java.util.List;

public record FunctionCallExpression(Expression function, List<Expression> arguments) implements Expression {

}
