package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.KeywordExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.NotImplementedException;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.analysis.AnalysisResult;
import dev.spiritstudios.mojank.meow.analysis.ClassType;
import dev.spiritstudios.mojank.meow.analysis.StructType;
import dev.spiritstudios.mojank.meow.analysis.Type;
import dev.spiritstudios.mojank.meow.binding.Alias;
import dev.spiritstudios.mojank.runtime.MeowBootstraps;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.TypeKind;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.methodDesc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.tryCast;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.writeCompilerResultStub;
import static java.lang.constant.ConstantDescs.CD_int;

/**
 * @author Ampflower
 **/
public final class Compiler<T> {
	private static final Logger logger = Util.logger();

	private final MethodHandles.Lookup lookup;
	private final Linker linker;
	private final Class<T> type;
	private final Method targetMethod;

	private final Map<String, IndexedParameter> parameters;
	private final int variablesIndex;

	private final ClassDesc compiledDesc;

	private final AnalysisResult analysis;

	Compiler(
		final MethodHandles.Lookup lookup,
		final Class<T> type,
		final Linker linker,
		Method targetMethod,
		Map<String, IndexedParameter> parameters,
		int variablesIndex,
		AnalysisResult analysis
	) {
		this.lookup = lookup;
		this.type = type;
		this.linker = linker;
		this.targetMethod = targetMethod;
		this.parameters = parameters;
		this.variablesIndex = variablesIndex;
		this.analysis = analysis;

		final var pack = lookup.lookupClass().getPackage().getName();

		this.compiledDesc = ClassDesc.of(pack, "\uD83C\uDFF3️\u200D⚧️️" + this.type.getSimpleName());
	}

	public byte[] compile(Expression expression, String source) {
		return compileToBytes(source, expression);
	}

	@SuppressWarnings("unchecked")
	public T compileAndInitialize(Expression expression, String source) {
		return (T) compileToResult(expression, source);
	}

	@VisibleForTesting
	public byte[] compileToBytes(
		final String program,
		final Expression expression
	) {
		return ClassFile.of().build(
			compiledDesc,
			builder -> {
				// Write the constructor and a few misc functions (toString, hashCode, etc.)
				writeCompilerResultStub(compiledDesc, type, targetMethod, program, builder);

				var locals = analysis.locals().getOrDefault(expression, StructType.EMPTY);

				var context = new CompileContext(locals);

				builder.withMethod(
					targetMethod.getName(),
					methodDesc(targetMethod.getReturnType(), targetMethod.getParameterTypes()),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					mb -> {
						mb.withCode(cob -> {
							// Fill in the LVT for the parameters based on the aliases since you can't reflectively access the names in non-ancient JVMs
							var params = targetMethod.getParameters();
							for (int i = 0; i < params.length; i++) {
								var param = params[i];
								var alias = param.getAnnotation(Alias.class);

								if (alias == null) continue;

								cob.localVariable(
									cob.parameterSlot(i),
									alias.value()[0],
									desc(param.getType()),
									cob.startLabel(), cob.endLabel()
								);
							}

							writeExpression(expression, cob, context, targetMethod.getReturnType());
						});
					}
				);
			}
		);
	}

	@VisibleForTesting
	public CompilerResult<T> define(byte[] bytecode) {
		try {
			final var result = analysis.variablesLookup() == null ?
				lookup.defineHiddenClass(bytecode, true) :
				this.lookup.defineHiddenClassWithClassData(bytecode, analysis.variablesLookup(), true);

			//noinspection unchecked
			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	private CompilerResult<T> compileToResult(
		Expression expression,
		final String program
	) {
		final var bytecode = compile(expression, program);

		return define(bytecode);
	}

	// region Locals
	private static int getLocalSlot(
		AccessExpression access,
		CodeBuilder builder,
		CompileContext context,
		Class<?> type
	) {
		var name = nameOf(access.fields());

		return context.locals().computeIfAbsent(
			name, k -> {
				var slot = builder.allocateLocal(TypeKind.from(type));

				builder.localVariable(
					slot,
					name,
					desc(type),
					builder.newBoundLabel(),
					builder.endLabel()
				);

				return slot;
			}
		);
	}

	// TODO: Proper local structs
	private Class<?> localGet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) {
			throw new UnsupportedOperationException("Local structs are currently unsupported.");
		}

		builder.fload(getLocalSlot(access, builder, context, clazz));

		return clazz;
	}

	private void localSet(AccessExpression access, Expression setTo, CodeBuilder builder, CompileContext context) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) {
			throw new UnsupportedOperationException("Local structs are currently unsupported.");
		}

		writeExpression(setTo, builder, context, clazz);

		builder.fstore(getLocalSlot(access, builder, context, clazz));
	}
	// endregion

	// region Fields
	private void fieldSet(AccessExpression access, Expression setTo, CodeBuilder builder, CompileContext context) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			localSet(access, setTo, builder, context);
			return;
		} else if (Linker.isVariable(access.first())) {
			variableSet(access, setTo, builder, context);
			return;
		}

		var param = parameters.get(first);

		Class<?> fieldType;
		String fieldName;

		if (param != null) {
			builder.loadInstruction(
				TypeKind.ReferenceType,
				builder.parameterSlot(param.index())
			);

			fieldType = param.parameter().getType();
		} else {
			fieldType = linker.findClass(first).orElseThrow();
		}


		List<String> fields = access.fields();
		for (int i = 0; i < fields.size(); i++) {
			fieldName = fields.get(i);
			var newField = linker.findField(fieldType, fieldName);

			var put = false;

			if (i == fields.size() - 1) {
				writeExpression(setTo, builder, context, newField.getType());
				put = true;
			}

			final int fieldMods = newField.getModifiers();

			if (put && Modifier.isFinal(fieldMods)) {
				throw new IllegalArgumentException("Cannot set final field '" + fieldName + "'");
			}

			builder.fieldInstruction(
				Modifier.isStatic(fieldMods) ?
					put ? Opcode.PUTSTATIC : Opcode.GETSTATIC :
					put ? Opcode.PUTFIELD : Opcode.GETFIELD,
				desc(fieldType),
				fieldName,
				desc(newField.getType())
			);

			fieldType = newField.getType();
		}
	}

	private Class<?> fieldGet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			return localGet(access, builder, context);
		} else if (Linker.isVariable(first)) {
			return variableGet(access, builder);
		}

		var param = parameters.get(first);

		Class<?> fieldType;
		String fieldName;

		if (param != null) {
			builder.loadInstruction(
				TypeKind.ReferenceType,
				builder.parameterSlot(param.index())
			);

			fieldType = param.parameter().getType();
		} else {
			fieldType = linker.findClass(first).orElseThrow();
		}


		List<String> fields = access.fields();
		for (String field : fields) {
			fieldName = field;
			var newField = linker.findField(fieldType, fieldName);

			if (Modifier.isStatic(newField.getModifiers())) {
				builder.fieldInstruction(
					Opcode.GETSTATIC,
					desc(fieldType),
					fieldName,
					desc(newField.getType())
				);
			} else {
				builder.fieldInstruction(
					Opcode.GETFIELD,
					desc(fieldType),
					fieldName,
					desc(newField.getType())
				);
			}

			fieldType = newField.getType();
		}

		return fieldType;
	}

	// endregion

	// region Variables
	private Class<?> loadVariableExceptLastAndGetType(AccessExpression access, CodeBuilder builder) {
		boolean aloaded = false;
		Type type = analysis.variables();
		List<String> fields = access.fields();
		for (int i = 0; i < fields.size() - 1; i++) {
			String field = fields.get(i);
			if (type instanceof StructType(Map<String, Type> members)) {
				type = members.get(field);
			}

			if (type == null) {
				return void.class;
			}

			if (!aloaded) {
				builder.aload(variablesIndex);
				aloaded = true;
			}

			builder.invokedynamic(
				DynamicCallSiteDesc.of(
					MeowBootstraps.GET,
					field,
					methodDesc(
						Variables.class,
						Object.class
					)
				)
			);
		}

		if (type instanceof StructType(Map<String, Type> members)) {
			type = members.get(access.fields().getLast());
		}

		if (type == null) {
			return void.class;
		}

		if (!aloaded) {
			builder.aload(variablesIndex);
			aloaded = true;
		}

		return type.clazz();
	}

	private Class<?> variableGet(AccessExpression access, CodeBuilder builder) {
		var clazz = loadVariableExceptLastAndGetType(access, builder);

		if (clazz == void.class) return void.class;

		builder.invokedynamic(
			DynamicCallSiteDesc.of(
				MeowBootstraps.GET,
				access.fields().getLast(),
				methodDesc(
					clazz,
					Object.class
				)
			)
		);

		return clazz;
	}


	private void variableSet(
		AccessExpression access,
		Expression setTo,
		CodeBuilder builder,
		CompileContext context
	) {
		var clazz = loadVariableExceptLastAndGetType(access, builder);


		if (clazz != Object.class) writeExpression(setTo, builder, context, clazz);
		else {
			var type = writeExpression(setTo, builder, context, null);
			var primitive = Primitive.primitiveLookup.get(type);
			if (primitive != null) primitive.box(builder);
		}
		builder.invokedynamic(
			DynamicCallSiteDesc.of(
				MeowBootstraps.SET,
				access.fields().getLast(),
				methodDesc(
					void.class,
					Variables.class,
					clazz
				)
			)
		);
	}
	// endregion

	private Class<?> writeFunctionCall(
		FunctionCallExpression functionCall,
		CodeBuilder builder,
		CompileContext context
	) {
		if (!(functionCall.function() instanceof AccessExpression access)) {
			throw new IllegalStateException("Function call must have an access on the left.");
		}

		if (Linker.isLoop(access)) {
			writeLoop(functionCall.arguments().getFirst(), functionCall.arguments().get(1), builder, context);

			return void.class;
		} else {
			return writeMethodCall(functionCall, builder, context, access);
		}
	}

	private Class<?> writeMethodCall(
		FunctionCallExpression functionCall,
		CodeBuilder builder,
		CompileContext context,
		AccessExpression access
	) {
		var method = linker.findMethod(access);

		var paramTypes = method.getParameterTypes();
		var mods = method.getModifiers();

		if (Modifier.isStatic(mods)) {
			List<Expression> arguments = functionCall.arguments();

			for (int i = 0; i < arguments.size(); i++) {
				Expression argument = arguments.get(i);
				Class<?> type = paramTypes[i];

				writeExpression(argument, builder, context, type);
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

	private Class<?> writeExpression(
		Expression exp,
		CodeBuilder builder,
		CompileContext context,
		@Nullable Class<?> expected
	) {
		Class<?> type = switch (exp) {
			case ComplexExpression expression -> {
				builder.block(block -> {
					for (Expression subExpression : expression.expressions()) {
						writeExpression(subExpression, block, context, null);
					}
				});

				yield void.class;
			}
			case FunctionCallExpression expression -> writeFunctionCall(expression, builder, context);
			case AccessExpression access -> fieldGet(access, builder, context);
			case NumberExpression num -> {
				var constant = expected == null ? num.value() : tryCast(num.value(), expected);
				builder.constantInstruction(constant);
				yield expected != null ? expected : float.class;
			}
			case BinaryOperationExpression bin -> switch (bin.operator()) {
				case SET -> {
					switch (bin.left()) {
						case AccessExpression access -> fieldSet(access, bin.right(), builder, context);
						case null, default -> throw new UnsupportedOperationException();
					}

					yield void.class;
				}
				case NULL_COALESCE -> {
					if (writeExpression(bin.left(), builder, context, expected) == void.class) {
						writeExpression(bin.right(), builder, context, expected);
					} else {
						builder.dup();

						builder.ifThen(
							Opcode.IFNULL,
							n -> {
								n.pop();
								writeExpression(bin.right(), n, context, expected);
							}
						);
					}

					yield expected;
				}
				case CONDITIONAL -> {
					writeIf(
						bin.left(),
						b -> writeExpression(bin.right(), b, context, expected),
						null,
						builder,
						context
					);

					yield expected;
				}
				case ADD -> {
					var left = writeExpression(bin.left(), builder, context, null);
					tryCast(left, float.class, builder);

					var right = writeExpression(bin.right(), builder, context, null);
					tryCast(right, float.class, builder);
					// TODO: Handle arithmetic properly

					builder.fadd();

					yield float.class;
				}
				case SUBTRACT -> {
					var left = writeExpression(bin.left(), builder, context, null);
					tryCast(left, float.class, builder);

					var right = writeExpression(bin.right(), builder, context, null);
					tryCast(right, float.class, builder);

					builder.fsub();

					yield float.class;
				}
				case MULTIPLY -> {
					var left = writeExpression(bin.left(), builder, context, null);
					tryCast(left, float.class, builder);

					var right = writeExpression(bin.right(), builder, context, null);
					tryCast(right, float.class, builder);

					builder.fmul();

					yield float.class;
				}
				case DIVIDE -> {
					var left = writeExpression(bin.left(), builder, context, null);
					tryCast(left, float.class, builder);

					var right = writeExpression(bin.right(), builder, context, null);
					tryCast(right, float.class, builder);

					builder.fdiv();

					yield float.class;
				}
				case ARROW -> throw new NotImplementedException();
				default -> {
					if (!writeBinaryIf(
						CodeBuilder::iconst_1,
						CodeBuilder::iconst_0,
						builder,
						context,
						bin.left(),
						bin.operator(),
						bin.right()
					)) {
						throw new NotImplementedException("Missing binary if operator impl for " + bin.operator());
					}

					yield boolean.class;
				}
			};
			case UnaryOperationExpression unary -> switch (unary.operator()) {
				case NEGATE -> {
					writeExpression(unary.value(), builder, context, float.class);
					builder.fneg();

					yield float.class;
				}
				case LOGICAL_NEGATE -> {
					writeExpression(unary.value(), builder, context, boolean.class);

					builder.ifThenElse(
						Opcode.IFNE,
						CodeBuilder::iconst_0,
						CodeBuilder::iconst_1
					);

					yield boolean.class;
				}
				case RETURN -> {
					writeExpression(unary.value(), builder, context, targetMethod.getReturnType());
					builder.returnInstruction(
						TypeKind.fromDescriptor(
							targetMethod.getReturnType().descriptorString()
						)
					);

					yield void.class;
				}
			};
			case TernaryOperationExpression ternary -> {
				writeIf(
					ternary.condition(),
					b -> writeExpression(ternary.ifTrue(), b, context, expected),
					b -> writeExpression(ternary.ifFalse(), b, context, expected),
					builder, context
				);

				yield expected;
			}
			case ArrayAccessExpression arrayAccess -> {
				var array = writeExpression(arrayAccess.array(), builder, context, null);

				if (!array.isArray()) {
					throw new IllegalStateException("Cannot index a " + array);
				}

				writeExpression(arrayAccess.index(), builder, context, int.class);

				// TODO: constant inline this when the input is constant.
				builder
					.iconst_0()
					.invokestatic(desc(Math.class), "max", MethodTypeDesc.of(CD_int, CD_int, CD_int))
					// TODO: We could reorder this so that the array is loaded at this point,
					//  then just dup_x1 it behind the int as well.
					//  Although this whole operation is frankly nonsensical and needs more thought.
					.swap()
					.dup_x1()
					.arraylength()
					.irem()
					// actual array load operation
					.arrayLoadInstruction(TypeKind.from(array.componentType()));

				yield array.componentType();
			}
			case KeywordExpression keyword -> {
				switch (keyword) {
					case BREAK -> {
						var loop = context.loops().peek();
						if (loop == null) throw new IllegalStateException("Tried to break when not inside a loop!");
						builder.goto_(loop.break_());
					}
					case CONTINUE -> {
						var loop = context.loops().peek();
						if (loop == null) throw new IllegalStateException("Tried to continue when not inside a loop!");
						builder.goto_(loop.continue_());
					}
				}

				yield void.class;
			}
			case StringExpression string -> {
				builder.ldc(string.value());

				yield String.class;
			}
		};

		if (expected != null) {
			tryCast(type, expected, builder);
		}

		return type;
	}

	private void writeLoop(Expression count, Expression code, CodeBuilder builder, CompileContext context) {
		builder.block(b -> {
			int indexSlot = b.allocateLocal(TypeKind.IntType);
			b.localVariable(
					indexSlot,
					BoilerplateGenerator.loopIndexName(context.loops().size()),
					CD_int,
					b.startLabel(),
					b.endLabel()
				)
				.iconst_0()
				.istore(indexSlot);

			var continue_ = b.newLabel();

			var start = b.newBoundLabel();

			b.iload(indexSlot);

			writeExpression(count, b, context, int.class);

			b.if_icmpge(b.breakLabel());

			context.loops().push(new Loop(
				continue_,
				b.breakLabel()
			));

			writeExpression(code, b, context, null);

			context.loops().pop();

			b
				.labelBinding(continue_)
				.iinc(indexSlot, 1)
				.goto_(start);
		});
	}

	private void writeIf(
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
			writeExpression(condition, builder, context, boolean.class);

			ifThenElse(
				builder,
				Opcode.IFNE,
				ifTrue,
				ifFalse
			);
		}
	}

	private boolean writeBinaryIf(
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
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFGT,
					ifTrue,
					ifFalse
				);
			}
			case LESS_THAN -> {
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpg();

				ifThenElse(
					builder,
					Opcode.IFLT,
					ifTrue,
					ifFalse
				);
			}
			case GREATER_THAN_OR_EQUAL_TO -> {
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFGE,
					ifTrue,
					ifFalse
				);
			}
			case LESS_THAN_OR_EQUAL_TO -> {
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpg();

				ifThenElse(
					builder,
					Opcode.IFLE,
					ifTrue,
					ifFalse
				);
			}
			case LOGICAL_OR -> {
				writeExpression(left, builder, context, boolean.class);

				builder.ifThenElse(
					Opcode.IFNE,
					ifTrue,
					b -> {
						writeExpression(right, builder, context, boolean.class);

						ifThenElse(
							builder,
							Opcode.IFNE,
							ifTrue,
							ifFalse
						);
					}
				);
			}
			case LOGICAL_AND -> {
				writeExpression(left, builder, context, boolean.class);

				ifThenElse(
					builder,
					Opcode.IFNE,
					b -> {
						writeExpression(right, b, context, boolean.class);
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

	private void writeEquality(
		boolean eq,
		Consumer<CodeBuilder.BlockCodeBuilder> ifTrue,
		@Nullable Consumer<CodeBuilder.BlockCodeBuilder> ifFalse,
		CodeBuilder builder,
		CompileContext context,
		Expression left,
		Expression right
	) {
		var leftType = writeExpression(left, builder, context, null);

		if (Object.class.isAssignableFrom(leftType)) {
			writeExpression(right, builder, context, Object.class);

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

			writeExpression(right, builder, context, primitive.primitive);

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

	private void ifThenElse(CodeBuilder builder, Opcode opcode, Consumer<CodeBuilder.BlockCodeBuilder> ifTrue, @Nullable
	Consumer<CodeBuilder.BlockCodeBuilder> ifFalse) {
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

	private static String nameOf(List<String> fields) {
		StringBuilder builder = new StringBuilder();

		for (String field : fields) {
			builder.append(field).append("$");
		}

		builder.deleteCharAt(builder.length() - 1);

		return builder.toString();
	}
}
