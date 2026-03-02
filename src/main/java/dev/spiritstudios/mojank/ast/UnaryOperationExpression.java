package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.BuiltinOperators;
import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.compile.Primitive;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import org.jetbrains.annotations.NotNull;

public record UnaryOperationExpression(Expression value, Operator operator) implements Expression {
	public enum Operator {
		POSITIVE,
		NUMERICAL_NEGATE,
		LOGICAL_NEGATE,
		RETURN
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("UnaryOperation[").append(operator.toString()).append(", ");
		value.append(builder);
		builder.append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}

	public static UnaryOperationExpression return_(Expression exp) {
		return new UnaryOperationExpression(exp, Operator.RETURN);
	}

	@Override
	public Class<?> type(CompileContext context) {
		return switch (operator) {
			case POSITIVE, NUMERICAL_NEGATE -> value.type(context);
			case LOGICAL_NEGATE -> boolean.class;
			case RETURN -> void.class;
		};
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		return switch (operator) {
			case POSITIVE -> value.emit(context, builder);
			case NUMERICAL_NEGATE -> {
				var type = value.emit(context, builder);

				BuiltinOperators.negate(type, builder);

				yield type;
			}
			case LOGICAL_NEGATE -> {
				var type = value.emit(context, builder);


				Primitive.downcastToBoolean(builder, type);

				builder.ifThenElse(
					Opcode.IFNE,
					CodeBuilder::iconst_0,
					CodeBuilder::iconst_1
				);

				yield boolean.class;
			}
			case RETURN -> {
				var type = value.emit(context, builder);
				var returnType = context.target().getReturnType();

				BoilerplateGenerator.tryCast(
					type,
					returnType,
					builder
				);

				builder.return_(TypeKind.from(returnType));
				yield void.class;
			}
		};
	}
}
