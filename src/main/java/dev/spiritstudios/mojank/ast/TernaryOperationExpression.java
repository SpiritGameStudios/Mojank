package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.compile.Primitive;

import java.lang.classfile.CodeBuilder;

import org.jetbrains.annotations.NotNull;

public record TernaryOperationExpression(Expression condition, Expression ifTrue, Expression ifFalse) implements
	Expression {
	@Override
	public Class<?> type(CompileContext context) {
		return null;
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		Primitive.downcastToBoolean(
			builder,
			condition.emit(context, builder)
		);

		final Class<?>[] ifTrueType = {null};
		final Class<?>[] ifFalseType = {null};

		builder.ifThenElse(
			b -> ifTrueType[0] = ifTrue.emit(context, b),
			b -> ifFalseType[0] = ifFalse.emit(context, b)
		);

		if (ifTrueType[0] == ifFalseType[0]) {
			return ifTrueType[0];
		} else {
			throw new UnsupportedOperationException("Both sides of ternary must return the same type");
		}
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Ternary[").pushIndent().newline();
		condition.append(builder);
		builder.append(",").newline();
		builder.append("ifTrue = ");
		ifTrue.append(builder);
		builder.append(",").newline();
		builder.append("ifFalse = ");
		ifFalse.append(builder);
		builder.popIndent().newline().append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
