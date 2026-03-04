package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.CompileContext;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.MethodTypeDesc;

import static dev.spiritstudios.mojank.compile.BoilerplateGenerator.desc;
import static java.lang.constant.ConstantDescs.CD_int;

public record ArrayAccessExpression(Expression array, Expression index) implements Expression {
	@Override
	public Class<?> type(CompileContext context) {
		return array.type(context).componentType();
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		var arrayType = array.emit(context, builder);

		if (!arrayType.isArray()) {
			throw new IllegalStateException("Cannot index a " + array);
		}

		BoilerplateGenerator.tryCast(
			index.emit(context, builder),
			int.class,
			builder
		);

		// TODO: constant inline this when the input is constant.
		BoilerplateGenerator.wrapArrayIndex(builder);

		var componentType = arrayType.componentType();

		builder.arrayLoad(TypeKind.from(componentType));

		return componentType;
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("ArrayAccess[");
		array.append(builder);
		builder.append(", ");
		index.append(builder);
		builder.append("]");
	}
}
