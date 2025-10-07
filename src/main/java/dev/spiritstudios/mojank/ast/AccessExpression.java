package dev.spiritstudios.mojank.ast;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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
