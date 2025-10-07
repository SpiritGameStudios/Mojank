package dev.spiritstudios.mojank.ast;

import java.util.List;

public record ComplexExpression(List<Expression> expressions) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("ComplexExpression {").pushIndent();
		expressions.forEach(exp -> {
			builder.newline();
			exp.append(builder);
		});
		builder.popIndent().newline().append("}");
	}
}
