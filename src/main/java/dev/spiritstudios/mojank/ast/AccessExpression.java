package dev.spiritstudios.mojank.ast;

public record AccessExpression(Expression object, String toAccess) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Access[");
		object.append(builder);
		builder.append(", ").append("\"").append(toAccess).append("\"]");
	}
}
