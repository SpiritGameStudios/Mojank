package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.compile.CompileContext;

import java.lang.classfile.CodeBuilder;

public sealed interface Expression permits ArrayAccessExpression, BinaryOperationExpression, ComplexExpression,
	ConstantExpression, MethodCallExpression, KeywordExpression, IdentifierExpression, LoopExpression,
	TernaryOperationExpression, UnaryOperationExpression {

	Class<?> type(CompileContext context);

	/// @implSpec MUST always return the same value as [Expression#type(CompileContext)] when given the same [CompileContext]
	Class<?> emit(CompileContext context, CodeBuilder builder);

	default void append(IndentedStringBuilder builder) {
		builder.append(this.toString());
	}

	default String toStr() {
		var builder = new IndentedStringBuilder(new StringBuilder());
		append(builder);
		return builder.toString();
	}
}
