package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.compile.CompileContext;

import java.lang.classfile.CodeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ComplexExpression(List<Expression> expressions) implements Expression {
	@Override
	public Class<?> type(CompileContext context) {
		return void.class;
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		for (Expression expression : expressions) {
			expression.emit(context, builder);
		}

		return void.class;
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("ComplexExpression {").pushIndent();
		expressions.forEach(exp -> {
			builder.newline();
			exp.append(builder);
		});
		builder.popIndent().newline().append("}");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
