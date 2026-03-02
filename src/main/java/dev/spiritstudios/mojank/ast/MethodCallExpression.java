package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.internal.NotImplementedException;
import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.CompileContext;

import java.lang.classfile.CodeBuilder;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static dev.spiritstudios.mojank.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.compile.BoilerplateGenerator.methodDesc;

public record MethodCallExpression(Expression method, List<Expression> parameters) implements Expression {
	public MethodCallExpression(MethodCallExpression function, Expression... arguments) {
		this(function, Arrays.asList(arguments));
	}

	@Override
	public Class<?> type(CompileContext context) {
		return context.linker().findMethod(method).getReturnType();
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		var method = context.linker().findMethod(method);

		var paramTypes = method.getParameterTypes();
		var mods = method.getModifiers();

		if (Modifier.isStatic(mods)) {
			for (int i = 0; i < parameters.size(); i++) {
				Expression argument = parameters.get(i);
				Class<?> type = paramTypes[i];

				BoilerplateGenerator.tryCast(
					argument.emit(context, builder),
					type,
					builder
				);
			}

			builder.invokestatic(
				desc(method.getDeclaringClass()),
				method.getName(),
				methodDesc(method.getReturnType(), method.getParameterTypes()),
				method.getDeclaringClass().isInterface()
			);
		} else {
			throw new NotImplementedException();
		}

		return method.getReturnType();
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("MethodCall[").pushIndent().newline();
		method.append(builder);
		builder.append(",").newline().append("parameters = [").pushIndent();
		for (Expression argument : parameters) {
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
