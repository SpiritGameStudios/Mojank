package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.internal.NotImplementedException;
import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.CompileContext;

import java.lang.classfile.CodeBuilder;

import org.jetbrains.annotations.NotNull;

import java.lang.classfile.Opcode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
		if (!(method instanceof BinaryOperationExpression binaryOp))
			throw new NotImplementedException("TODO: non binaryop methodcalls");
		if (!(binaryOp.right() instanceof IdentifierExpression(String methodName)))
			throw new IllegalStateException("Right of method access is not an identifier.");

		var objectType = binaryOp.left().type(context);

		var method = context.linker().findMethod(objectType, methodName);

		if (method == null) {
			throw new IllegalStateException("No method with name '" + methodName + "' on class '" + objectType + "' was found.");
		}

		return method.getReturnType();
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		// TODO: Clearer errors
		if (!(method instanceof BinaryOperationExpression binaryOp))
			throw new NotImplementedException("TODO: non binaryop methodcalls");

		if (!(binaryOp.right() instanceof IdentifierExpression(String methodName)))
			throw new IllegalStateException("Right of method access is not an identifier.");

		Class<?> owner = null;
		Method method = null;

		if (binaryOp.left() instanceof IdentifierExpression(String name)) {
			var clazz = context.linker().findClass(name);

			if (clazz != null) {
				owner = clazz;
				method = context.linker().findMethod(clazz, methodName);
			}
		}

		if (method == null) {
			var leftType = binaryOp.left().emit(context, builder);

			owner = leftType;
			method = context.linker().findMethod(leftType, methodName);
		}


		if (method == null) {
			throw new IllegalStateException("No method with name '" + methodName + "' on class '" + owner + "' was found.");
		}

		var modifiers = method.getModifiers();

		for (int i = 0; i < parameters.size(); i++) {
			var param = parameters.get(i);

			var type = param.emit(context, builder);
			BoilerplateGenerator.tryCast(type, method.getParameterTypes()[i], builder);
		}

		builder.invoke(
			Modifier.isStatic(modifiers) ?
				Opcode.INVOKESTATIC :
				owner.isInterface() ? Opcode.INVOKEINTERFACE : Opcode.INVOKEVIRTUAL,
			desc(owner),
			method.getName(),
			methodDesc(method.getReturnType(), method.getParameterTypes()),
			owner.isInterface()
		);

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
