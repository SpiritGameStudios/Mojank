package dev.spiritstudios.mojank.ast;

public record StringExpression(String value) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("String(\"").append(value).append("\")");
	}
}
