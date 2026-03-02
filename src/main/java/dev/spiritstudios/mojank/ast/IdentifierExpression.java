package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

public record IdentifierExpression(String name) implements Expression {
	@Override
	public Class<?> type(CompileContext context) {
		var parameter = context.parameters().get(name);
		if (parameter != null) return parameter.type();

		throw new IllegalStateException("Unknown identifier '" + name + "'");
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		var parameter = context.parameters().get(name);

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
