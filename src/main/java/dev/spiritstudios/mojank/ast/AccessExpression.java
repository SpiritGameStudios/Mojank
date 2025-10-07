package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record AccessExpression(Expression object, String toAccess) implements Expression {
	public AccessExpression(String object, String toAccess) {
		this(new IdentifierExpression(object), toAccess);
	}


	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Access[");
		object.append(builder);
		builder.append(", ").append("\"").append(toAccess).append("\"]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
