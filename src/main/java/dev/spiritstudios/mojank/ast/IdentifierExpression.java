package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import static dev.spiritstudios.mojank.compile.Descriptors.desc;

public record IdentifierExpression(String name) implements Expression {
	public boolean isClass(CompileContext context) {
		var clazz = context.linker().findClass(name);
		return clazz != null;
	}

	@Override
	public Class<?> type(CompileContext context) {
		var clazz = context.linker().findClass(name);
		if (clazz != null) return Class.class;

		var parameter = context.parametersByName().get(name);
		if (parameter != null) return parameter.type();

		throw new IllegalStateException("Unknown identifier '" + name + "'");
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		var clazz = context.linker().findClass(name);

		if (clazz != null) {
			builder.loadConstant(desc(clazz));

			return Class.class;
		}

		var parameter = context.parametersByName().get(name);

		if (parameter != null) {
			builder.loadLocal(TypeKind.from(parameter.type()), parameter.index());

			return parameter.type();
		}

		throw new IllegalStateException("Unknown identifier '" + name + "'");
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("LocalAccess[").append(name).append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}

}
