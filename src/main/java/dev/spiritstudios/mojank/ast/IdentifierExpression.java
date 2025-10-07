package dev.spiritstudios.mojank.ast;

public record IdentifierExpression(String value) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Id(").append(value).append(")");
	}
}
