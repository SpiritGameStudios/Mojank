package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.Conditionals;
import dev.spiritstudios.mojank.compile.link.Linker;
import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.internal.NotImplementedException;
import dev.spiritstudios.mojank.compile.BuiltinOperators;
import dev.spiritstudios.mojank.compile.CompileContext;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static dev.spiritstudios.mojank.compile.BoilerplateGenerator.desc;

public record BinaryOperationExpression(Expression left, Operator operator, Expression right) implements Expression {
	public enum Operator {
		SET(0),
		NULL_COALESCE(1),
		CONDITIONAL(2),
		LOGICAL_OR(3),
		LOGICAL_AND(4),
		EQUAL_TO(5),
		NOT_EQUAL(5),
		LESS_THAN(6),
		GREATER_THAN(6),
		LESS_THAN_OR_EQUAL_TO(6),
		GREATER_THAN_OR_EQUAL_TO(6),
		ADD(7),
		SUBTRACT(7),
		MULTIPLY(8),
		DIVIDE(8),
		REMAINDER(8),
		GET(999),
		ARROW(999);

		public final int precedence;

		Operator(int precedence) {
			this.precedence = precedence;
		}
	}

	@Override
	public Class<?> type(CompileContext context) {
		return null;
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		return switch (operator) {
			case SET -> {
				switch (left) {
					case BinaryOperationExpression leftOp -> {
						var leftType = leftOp.left.emit(context, builder);
						if (!(leftOp.right instanceof IdentifierExpression(String identifier)))
							throw new IllegalStateException("Right side of . must be an identifier");

						Field field = context.linker().findField(leftType, identifier);

						if (field == null) throw new NotImplementedException("TODO: method gets");

						var modifiers = field.getModifiers();

						if (Modifier.isStatic(modifiers)) throw new NotImplementedException("TODO: Statics");

						var rightType = right.emit(context, builder);

						BoilerplateGenerator.tryCast(rightType, field.getType(), builder);

						builder.fieldAccess(
							Opcode.PUTFIELD,
							desc(leftType),
							field.getName(),
							desc(field.getType())
						);

						yield void.class;
					}
					case ArrayAccessExpression leftOp -> {
						var arrayType = leftOp.array().emit(context, builder);
						var componentType = arrayType.componentType();

						BoilerplateGenerator.tryCast(
							leftOp.index().emit(context, builder),
							int.class,
							builder
						);

						// TODO: constant inline this when the input is constant.
						BoilerplateGenerator.wrapArrayIndex(builder);

						var rightType = right.emit(context, builder);

						BoilerplateGenerator.tryCast(rightType, componentType, builder);

						builder.arrayStore(TypeKind.from(componentType));

						yield void.class;
					}
					case null, default -> throw new UnsupportedOperationException();
				}

			}
			case NULL_COALESCE -> {
				throw new NotImplementedException();
//				var t = left.emit(context, builder);
//
//				if (t == void.class) {
//					right.emit(context, builder);
//				} else {
//					builder.dup();
//
//					builder.ifThen(
//						Opcode.IFNULL,
//						n -> {
//							n.pop();
//							right.emit(context, builder);
//						}
//					);
//				}
//
//				yield expected;
			}
			case CONDITIONAL -> {
				Conditionals.writeIf(
					left,
					b -> right.emit(context, b),
					null,
					builder,
					context
				);

				throw new NotImplementedException();
			}
			case ADD -> {
				var leftType = left.emit(context, builder);
				var rightType = right.emit(context, builder);

				if (leftType != rightType) {
					throw new UnsupportedOperationException("Cannot perform operation '" + leftType + " + " + rightType + "'");
				}

				BuiltinOperators.add(leftType, builder);

				yield rightType;
			}
			case SUBTRACT -> {
				var leftType = left.emit(context, builder);
				var rightType = right.emit(context, builder);

				if (leftType != rightType) {
					throw new UnsupportedOperationException("Cannot perform operation '" + leftType + " - " + rightType + "'");
				}

				BuiltinOperators.subtract(leftType, builder);

				yield rightType;
			}
			case MULTIPLY -> {
				var leftType = left.emit(context, builder);
				var rightType = right.emit(context, builder);

				if (leftType != rightType) {
					throw new UnsupportedOperationException("Cannot perform operation '" + leftType + " * " + rightType + "'");
				}

				BuiltinOperators.multiply(leftType, builder);

				yield rightType;
			}
			case DIVIDE -> {
				var leftType = left.emit(context, builder);
				var rightType = right.emit(context, builder);

				if (leftType != rightType) {
					throw new UnsupportedOperationException("Cannot perform operation '" + leftType + " / " + rightType + "'");
				}

				BuiltinOperators.divide(leftType, builder);

				yield rightType;
			}
			case REMAINDER -> {
				var leftType = left.emit(context, builder);
				var rightType = right.emit(context, builder);

				if (leftType != rightType) {
					throw new UnsupportedOperationException("Cannot perform operation '" + leftType + " % " + rightType + "'");
				}

				BuiltinOperators.remainder(leftType, builder);

				yield rightType;
			}
			case GET -> {
				if (!(right instanceof IdentifierExpression(String fieldName))) throw new IllegalStateException("Right side of . must be an identifier");

				Class<?> owner = null;
				Field field = null;

				if (left instanceof IdentifierExpression(String name)) {
					var clazz = context.linker().findClass(name);

					if (clazz != null) {
						owner = clazz;
						field = context.linker().findField(clazz, fieldName);
					}
				}

				if (field == null) {
					var leftType = left.emit(context, builder);

					owner = leftType;
					field = context.linker().findField(leftType, fieldName);
				}

				if (field == null) throw new NotImplementedException("TODO: method gets");

				var modifiers = field.getModifiers();

				builder.fieldAccess(
					Modifier.isStatic(modifiers) ? Opcode.GETSTATIC : Opcode.GETFIELD,
					desc(owner),
					field.getName(),
					desc(field.getType())
				);

				yield field.getType();
			}
			case ARROW -> throw new NotImplementedException();
			default -> {
				if (!Conditionals.writeBinaryIf(
					CodeBuilder::iconst_1,
					CodeBuilder::iconst_0,
					builder,
					context,
					left, operator, right
				)) {
					throw new NotImplementedException("Missing binary if operator impl for " + operator);
				}

				yield boolean.class;
			}
		};
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("BinaryOperation(").pushIndent().newline();
		left.append(builder);
		builder.append(",").newline();
		builder.append(operator.toString());
		builder.append(",").newline();
		right.append(builder);
		builder.popIndent().newline().append(")");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
