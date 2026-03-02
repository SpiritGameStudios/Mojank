package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.compile.Primitive;

import java.lang.classfile.CodeBuilder;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ConstantDesc;

public record ConstantExpression(ConstantDesc value) implements Expression {
	public static final ConstantExpression ONE = new ConstantExpression(1.0F);
	public static final ConstantExpression ZERO = new ConstantExpression(0.0F);

	public static final ConstantExpression TRUE = new ConstantExpression(1);
	public static final ConstantExpression FALSE = new ConstantExpression(0);

	@Override
	public @NotNull String toString() {
		return "Constant(" + value + ")";
	}

	@Override
	public Class<?> type(CompileContext context) {
		return Primitive.unboxedType(value.getClass());
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		builder.loadConstant(value);
		return this.type(context);
	}
}
