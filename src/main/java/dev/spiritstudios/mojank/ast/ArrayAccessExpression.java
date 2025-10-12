package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;

public record ArrayAccessExpression(Expression array, Expression index) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("ArrayAccess[");
		array.append(builder);
		builder.append(", ");
		index.append(builder);
		builder.append("]");
	}
}
