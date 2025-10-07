package dev.spiritstudios.mojank.ast;

import java.util.List;

public record FunctionCallExpression(Expression function, List<Expression> arguments) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("FunctionCall[").pushIndent().newline();
		function.append(builder);
		builder.append(",").newline().append("arguments = [").pushIndent();
		for (Expression argument : arguments) {
			builder.newline();
			argument.append(builder);
			builder.append(",");
		}
		builder.popIndent().newline().append("]").popIndent().newline().append("]");
	}
}
