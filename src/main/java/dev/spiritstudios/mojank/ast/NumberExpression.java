package dev.spiritstudios.mojank.ast;

public record NumberExpression(float value) implements Expression {
	public static final NumberExpression ONE = new NumberExpression(1.0F);
	public static final NumberExpression ZERO = new NumberExpression(0.0F);

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Number(").append(String.valueOf(value)).append(")");
	}
}
