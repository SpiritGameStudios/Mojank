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
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.TypeKind;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.kindOf;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.methodDesc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.tryCast;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.writeCompilerResultStub;

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
		logger.info(source);
		logger.info(expression.toString());

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
		logger.info(program);

		return ClassFile.of().build(
			compiledDesc,
			builder -> {
				// Write the constructor and a few misc functions (toString, hashCode, etc.)
				writeCompilerResultStub(compiledDesc, type, targetMethod, program, builder);

				compile(builder, expression);
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

	private void compile(ClassBuilder builder, Expression expression) {
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

	// region Locals
	private static int getLocalSlot(
		AccessExpression access,
		CodeBuilder builder,
		CompileContext context,
		TypeKind type
	) {
		var name = nameOf(access.fields());

		return context.locals().computeIfAbsent(
			name, k -> {
				var slot = builder.allocateLocal(type);

				builder.localVariable(
					slot,
					name,
					ClassDesc.ofDescriptor(type.descriptor()),
					builder.newBoundLabel(),
					builder.endLabel()
				);

				return slot;
			}
		);
	}

	// TODO: Proper local structs
	private void localGet(AccessExpression access, CodeBuilder builder, CompileContext context, Class<?> expectedType) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) {
			throw new UnsupportedOperationException("Local structs are currently unsupported.");
		}

		builder.fload(getLocalSlot(access, builder, context, kindOf(clazz)));

		tryCast(clazz, expectedType, builder);
	}

	private void localSet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) {
			throw new UnsupportedOperationException("Local structs are currently unsupported.");
		}

		builder.fstore(getLocalSlot(access, builder, context, kindOf(clazz)));
	}
	// endregion

	// region Fields
	private void fieldSet(AccessExpression access, Expression setTo, CodeBuilder builder, CompileContext context) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			var fieldType = context.localsType().members().get(access.fields().getFirst());

			if (!(fieldType instanceof ClassType(Class<?> clazz)))
				throw new UnsupportedOperationException();

			writeExpression(setTo, builder, context, clazz);
			localSet(access, builder, context);
			return;
		}

		var param = parameters.get(first);

		Class<?> fieldType;
		int fieldMods = 0;
		String fieldName;

		if (param != null) {
			builder.loadInstruction(
				TypeKind.ReferenceType,
				builder.parameterSlot(param.index())
			);

			fieldType = param.parameter().getType();
			fieldMods = param.parameter().getModifiers();
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

	private void fieldGet(AccessExpression access, CodeBuilder builder, CompileContext context, Class<?> expectedType) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			localGet(access, builder, context, expectedType);
			return;
		}

		var param = parameters.get(first);

		Class<?> fieldType;
		int fieldMods = 0;
		String fieldName;

		if (param != null) {
			builder.loadInstruction(
				TypeKind.ReferenceType,
				builder.parameterSlot(param.index())
			);

			fieldType = param.parameter().getType();
			fieldMods = param.parameter().getModifiers();
		} else {
			fieldType = linker.findClass(first).orElseThrow();
		}


		List<String> fields = access.fields();
		for (String field : fields) {
			fieldName = field;
			var newField = linker.findField(fieldType, fieldName);

			if (Modifier.isStatic(fieldMods)) {
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

		tryCast(fieldType, expectedType, builder);
	}

	// endregion

	// region Variables
	private Class<?> loadVariableExceptLastAndGetType(AccessExpression access, CodeBuilder builder) {
		builder.aload(variablesIndex);

		Type type = analysis.variables();
		List<String> fields = access.fields();
		for (int i = 0; i < fields.size() - 1; i++) {
			String field = fields.get(i);
			if (type instanceof StructType(Map<String, Type> members)) {
				type = members.get(field);
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

		return switch (type) {
			case ClassType classType -> classType.clazz();
			case StructType ignored -> Object.class;
		};
	}

	private void variableGet(AccessExpression access, CodeBuilder builder, Class<?> expectedType) {
		var clazz = loadVariableExceptLastAndGetType(access, builder);

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

		tryCast(clazz, expectedType, builder);
	}


	private void variableSet(
		AccessExpression access,
		Expression setTo,
		CodeBuilder builder,
		CompileContext context
	) {
		var clazz = loadVariableExceptLastAndGetType(access, builder);

		writeExpression(setTo, builder, context, clazz);

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

	private void writeFunctionCall(
		FunctionCallExpression functionCall,
		CodeBuilder builder,
		Class<?> expectedType,
		CompileContext context
	) {
		if (!(functionCall.function() instanceof AccessExpression access)) {
			throw new IllegalStateException("Function call must have an access on the left.");
		}

		if (Linker.isLoop(access)) {
			writeLoop(functionCall.arguments().getFirst(), functionCall.arguments().get(1), builder, context);
		} else {
			writeMethodCall(functionCall, builder, expectedType, context, access);
		}
	}

	private void writeMethodCall(
		FunctionCallExpression functionCall,
		CodeBuilder builder,
		Class<?> expectedType,
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

		tryCast(method.getReturnType(), expectedType, builder);
	}

	private void writeExpression(Expression primitive,
								 CodeBuilder builder,
								 CompileContext context,
								 Class<?> expectedType) {
		switch (primitive) {
			case ComplexExpression expression -> {
				builder.block(block -> {
					for (Expression subExpression : expression.expressions()) {
						writeExpression(subExpression, block, context, Object.class);
					}
				});
			}
			case FunctionCallExpression expression -> {
				writeFunctionCall(expression, builder, expectedType, context);
			}
			case AccessExpression access -> {
				if (Linker.isVariable(access.first())) {
					variableGet(access, builder, expectedType);
				} else {
					fieldGet(access, builder, context, expectedType);
				}
			}
			case NumberExpression num -> {
				builder.constantInstruction(tryCast(num.value(), expectedType));
			}
			case BinaryOperationExpression bin -> {
				switch (bin.operator()) {
					case SET -> {
						switch (bin.left()) {
							case AccessExpression access -> {
								if (Linker.isVariable(access.first())) {
									variableSet(access, bin.right(), builder, context);
								} else {
									fieldSet(access, bin.right(), builder, context);
								}
							}
							case null, default -> throw new UnsupportedOperationException();
						}
					}
					case NULL_COALESCE -> {
						writeExpression(bin.left(), builder, context, expectedType);
						builder.dup();

						builder.ifThen(
							Opcode.IFNULL,
							n -> {
								n.pop();
								writeExpression(bin.right(), n, context, expectedType);
							}
						);
					}
					case CONDITIONAL -> {
						writeIf(
							bin.left(),
							b -> writeExpression(bin.right(), b, context, expectedType),
							null,
							builder,
							context
						);
					}
					case ADD -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fadd();

						tryCast(float.class, expectedType, builder);
					}
					case SUBTRACT -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fsub();

						tryCast(float.class, expectedType, builder);
					}
					case MULTIPLY -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fmul();

						tryCast(float.class, expectedType, builder);
					}
					case DIVIDE -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fdiv();

						tryCast(float.class, expectedType, builder);
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

						tryCast(int.class, expectedType, builder);
					}
				}
			}
			case UnaryOperationExpression unary -> {
				switch (unary.operator()) {
					case NEGATE -> {
						writeExpression(unary.value(), builder, context, float.class);
						builder.fneg();

						tryCast(float.class, expectedType, builder);
					}
					case LOGICAL_NEGATE -> {
						writeExpression(unary.value(), builder, context, int.class);

						builder.ifThenElse(
							Opcode.IFNE,
							CodeBuilder::iconst_0,
							CodeBuilder::iconst_1
						);

						tryCast(int.class, expectedType, builder);
					}
					case RETURN -> {
						writeExpression(unary.value(), builder, context, targetMethod.getReturnType());
						builder.returnInstruction(
							TypeKind.fromDescriptor(
								targetMethod.getReturnType().descriptorString()
							)
						);
					}
				}
			}
			case TernaryOperationExpression ternary -> {
				writeIf(
					ternary.condition(),
					b -> writeExpression(ternary.ifTrue(), b, context, expectedType),
					b -> writeExpression(ternary.ifFalse(), b, context, expectedType),
					builder, context
				);
			}
			case ArrayAccessExpression arrayAccess -> {
				writeExpression(arrayAccess.array(), builder, context, expectedType.arrayType());
				writeExpression(arrayAccess.index(), builder, context, int.class);

				builder.arrayLoadInstruction(kindOf(expectedType));
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
			}
			case StringExpression string -> {
				builder.ldc(string.value());

				tryCast(String.class, expectedType, builder);
			}
		}
	}

	private void writeLoop(Expression count, Expression code, CodeBuilder builder, CompileContext context) {
		builder.block(b -> {
			int indexSlot = b.allocateLocal(TypeKind.IntType);
			b.localVariable(
					indexSlot,
					BoilerplateGenerator.loopIndexName(context.loops().size()),
					ConstantDescs.CD_int,
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

			writeExpression(code, b, context, Object.class);

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
			writeExpression(condition, builder, context, int.class);

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
			// TODO: Non-float equality
			case EQUAL_TO -> {
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFEQ,
					ifTrue,
					ifFalse
				);
			}
			case NOT_EQUAL -> {
				writeExpression(left, builder, context, float.class);
				writeExpression(right, builder, context, float.class);

				builder.fcmpl();

				ifThenElse(
					builder,
					Opcode.IFNE,
					ifTrue,
					ifFalse
				);
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
				writeExpression(left, builder, context, int.class);

				builder.ifThenElse(
					Opcode.IFNE,
					ifTrue,
					b -> {
						writeExpression(right, builder, context, int.class);

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
				writeExpression(left, builder, context, int.class);

				ifThenElse(
					builder,
					Opcode.IFNE,
					b -> {
						writeExpression(right, b, context, int.class);
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
