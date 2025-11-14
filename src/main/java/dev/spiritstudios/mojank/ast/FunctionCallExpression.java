package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public record FunctionCallExpression(AccessExpression function, List<Expression> arguments) implements Expression {
	public FunctionCallExpression(AccessExpression function, Expression... arguments) {
		this(function, Arrays.asList(arguments));
	}

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

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
