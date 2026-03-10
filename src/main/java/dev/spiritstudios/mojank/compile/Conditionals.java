package dev.spiritstudios.mojank.compile;

import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.Expression;
import org.jetbrains.annotations.Nullable;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.spiritstudios.mojank.compile.Descriptors.desc;
import static dev.spiritstudios.mojank.compile.Descriptors.methodDesc;

public class Conditionals {
	public static void writeIf(
		Expression condition,
		Consumer<CodeBuilder.BlockCodeBuilder> ifTrue,
		@Nullable Consumer<CodeBuilder.BlockCodeBuilder> ifFalse,
		CodeBuilder builder,
		CompileContext context
	) {
		if (condition instanceof BinaryOperationExpression(
			Expression left, BinaryOperationExpression.Operator operator, Expression right
		)) {
			writeBinaryIf(ifTrue, ifFalse, builder, context, left, operator, right);
		} else {
			Primitive.downcastToBoolean(builder, condition.emit(context, builder));

			ifThenElse(
				builder,
				Opcode.IFNE,
				ifTrue,
				ifFalse
			);
		}
	}

	public static void ifThenElse(
		CodeBuilder builder,
		Opcode opcode,
		Consumer<CodeBuilder.BlockCodeBuilder> ifTrue,
		@Nullable
		Consumer<CodeBuilder.BlockCodeBuilder> ifFalse
	) {
		if (ifFalse == null) {
			builder.ifThen(
				opcode,
				ifTrue
			);
		} else {
			builder.ifThenElse(
				opcode,
				ifTrue,
				ifFalse
			);
		}
	}

	public static boolean writeBinaryIf(
		Consumer<CodeBuilder.BlockCodeBuilder> ifTrue,
		@Nullable Consumer<CodeBuilder.BlockCodeBuilder> ifFalse,
		CodeBuilder builder,
		CompileContext context,
		Expression left,
		BinaryOperationExpression.Operator operator,
		Expression right
	) {
		switch (operator) {
			case EQUAL_TO -> {
				writeEquality(true, ifTrue, ifFalse, builder, context, left, right);
			}
			case NOT_EQUAL -> {
				writeEquality(false, ifTrue, ifFalse, builder, context, left, right);
			}
			case GREATER_THAN -> {
				left.emit(context, builder);
				right.emit(context, builder);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFGT,
					ifTrue,
					ifFalse
				);
			}
			case LESS_THAN -> {
				left.emit(context, builder);
				right.emit(context, builder);

				builder.fcmpg();

				ifThenElse(
					builder,
					Opcode.IFLT,
					ifTrue,
					ifFalse
				);
			}
			case GREATER_THAN_OR_EQUAL_TO -> {
				left.emit(context, builder);
				right.emit(context, builder);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFGE,
					ifTrue,
					ifFalse
				);
			}
			case LESS_THAN_OR_EQUAL_TO -> {
				left.emit(context, builder);
				right.emit(context, builder);

				builder.fcmpg();

				ifThenElse(
					builder,
					Opcode.IFLE,
					ifTrue,
					ifFalse
				);
			}
			case LOGICAL_OR -> {
				left.emit(context, builder);

				builder.ifThenElse(
					Opcode.IFNE,
					ifTrue,
					b -> {
						right.emit(context, b);

						ifThenElse(
							b,
							Opcode.IFNE,
							ifTrue,
							ifFalse
						);
					}
				);
			}
			case LOGICAL_AND -> {
				left.emit(context, builder);

				ifThenElse(
					builder,
					Opcode.IFNE,
					b -> {
						right.emit(context, builder);

						ifThenElse(b, Opcode.IFNE, ifTrue, ifFalse);
					},
					ifFalse
				);
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	public static void writeEquality(
		boolean eq,
		Consumer<CodeBuilder.BlockCodeBuilder> ifTrue,
		@Nullable Consumer<CodeBuilder.BlockCodeBuilder> ifFalse,
		CodeBuilder builder,
		CompileContext context,
		Expression left,
		Expression right
	) {
		var leftType = left.emit(context, builder);

		if (Object.class.isAssignableFrom(leftType)) {
			right.emit(context, builder);

			builder.invokestatic(desc(Objects.class), "equals", methodDesc(boolean.class, Object.class, Object.class));

			ifThenElse(
				builder,
				eq ? Opcode.IFNE : Opcode.IFEQ,
				ifTrue,
				ifFalse
			);
		} else {
			var primitive = Primitive.primitiveLookup.get(leftType);

			if (primitive == null) {
				throw new UnsupportedOperationException("Cannot compare " + leftType);
			}

			BoilerplateGenerator.tryCast(
				right.emit(context, builder),
				primitive.primitive,
				builder
			);

			switch (primitive) {
				case Boolean, Byte, Short, Character, Integer -> {
					ifThenElse(
						builder,
						eq ? Opcode.IF_ICMPEQ : Opcode.IF_ICMPNE,
						ifTrue,
						ifFalse
					);

					return;
				}
				case Long -> builder.lcmp();
				case Float -> builder.fcmpl();
				case Double -> builder.dcmpl();
				default -> throw new UnsupportedOperationException("Cannot compare " + leftType);
			}

			ifThenElse(
				builder,
				eq ? Opcode.IFEQ : Opcode.IFNE,
				ifTrue,
				ifFalse
			);
		}
	}
}
