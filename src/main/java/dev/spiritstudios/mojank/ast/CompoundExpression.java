package dev.spiritstudios.mojank.ast;

import java.util.List;

public record CompoundExpression(List<Expression> expressions) implements Expression {

}
