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
import dev.spiritstudios.mojank.ast.VariableExpression;
import dev.spiritstudios.mojank.internal.NotImplementedException;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.analysis.AnalysisResult;
import dev.spiritstudios.mojank.meow.analysis.ClassType;
import dev.spiritstudios.mojank.meow.analysis.StructType;
import dev.spiritstudios.mojank.meow.analysis.Type;
import dev.spiritstudios.mojank.meow.compile.debug.DebugUtils;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.TypeKind;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.methodDesc;
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

	private byte[] compileToBytes(
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

	private CompilerResult<T> compileToResult(
		Expression expression,
		final String program
	) {
		final var bytecode = compile(expression, program);

		DebugUtils.debug(bytecode);

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

	private void compile(ClassBuilder builder, Expression expression) {
		var locals = analysis.locals().getOrDefault(expression, StructType.EMPTY);

		var context = new CompileContext(locals);

		builder.withMethod(
			targetMethod.getName(),
			methodDesc(targetMethod.getReturnType(), targetMethod.getParameterTypes()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			mb -> {
				mb.withCode(cob -> {
					writeExpression(expression, cob, context, targetMethod.getReturnType());
				});
			}
		);
	}

	// region Locals
	private static int getLocalIndex(AccessExpression access, CodeBuilder builder, CompileContext context, TypeKind type) {
		var name = nameOf(access.fields());
		return context.locals().computeIfAbsent(name, k -> builder.allocateLocal(type));
	}

	private void localGet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) throw new UnsupportedOperationException();

		builder.fload(getLocalIndex(access, builder, context, kindOf(clazz)));
	}

	private void localSet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var type = context.localsType().members().get(access.fields().getFirst());

		if (!(type instanceof ClassType(Class<?> clazz))) throw new UnsupportedOperationException();

		builder.fstore(getLocalIndex(access, builder, context, kindOf(clazz)));
	}
	// endregion

	private void fieldSet(Expression setTo, AccessExpression access, CodeBuilder builder, CompileContext context) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			var fieldType = context.localsType().members().get(access.fields().getFirst());

			if (!(fieldType instanceof ClassType(Class<?> clazz))) throw new UnsupportedOperationException();

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

	private void fieldGet(AccessExpression access, CodeBuilder builder, CompileContext context) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			localGet(access, builder, context);
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
					builder.constantPool().fieldRefEntry(
						desc(fieldType),
						fieldName,
						desc(newField.getType())
					)
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
	}

	// region Variables
	private Class<?> loadVariableExceptLastAndGetType(VariableExpression variable, CodeBuilder builder) {
		builder.aload(variablesIndex);

		Type type = analysis.variables();
		List<String> fields = variable.fields();
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
			type = members.get(variable.fields().getLast());
		}

		return switch (type) {
			case ClassType classType -> classType.clazz();
			case StructType ignored -> Object.class;
		};
	}

	private void variableGet(VariableExpression variable, CodeBuilder builder, Class<?> expectedType) {
		var clazz = loadVariableExceptLastAndGetType(variable, builder);

		if (!expectedType.isAssignableFrom(clazz)) {
			throw new IllegalStateException("expected: " + expectedType + ", got: " + clazz);
		}

		builder.invokedynamic(
			DynamicCallSiteDesc.of(
				MeowBootstraps.GET,
				variable.fields().getLast(),
				methodDesc(
					clazz,
					Object.class
				)
			)
		);
	}


	private void variableSet(
		VariableExpression variable,
		Expression setTo,
		CodeBuilder builder,
		CompileContext context
	) {
		var clazz = loadVariableExceptLastAndGetType(variable, builder);

		writeExpression(setTo, builder, context, clazz);

		builder.invokedynamic(
			DynamicCallSiteDesc.of(
				MeowBootstraps.SET,
				variable.fields().getLast(),
				methodDesc(
					void.class,
					Variables.class,
					clazz
				)
			)
		);
	}
	// endregion

	private void functionCall(
		FunctionCallExpression functionCall,
		CodeBuilder builder,
		Class<?> expected,
		CompileContext context
	) {
		if (!(functionCall.function() instanceof AccessExpression access)) {
			throw new RuntimeException();
		}

		var method = linker.findMethod(access);
		if (!expected.isAssignableFrom(method.getReturnType())) {
			throw new IllegalStateException("uwu you fucked up your return types meow");
		}

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
	}


	private void writeExpression(Expression primitive,
								 CodeBuilder builder,
								 CompileContext context,
								 Class<?> expectedType) {
		switch (primitive) {
			case ComplexExpression expression -> {
				for (Expression subExpression : expression.expressions()) {
					writeExpression(subExpression, builder, context, Object.class);
				}
			}
			case FunctionCallExpression expression -> {
				functionCall(expression, builder, expectedType, context);
			}
			case AccessExpression access -> {
				fieldGet(access, builder, context);
			}
			case VariableExpression variable -> {
				variableGet(variable, builder, expectedType);
			}
			case NumberExpression num -> {
				if (!expectedType.isAssignableFrom(float.class)) {
					throw new IllegalStateException("Expected: " + expectedType + ", Got: float");
				}

				builder.constantInstruction(num.value()); // push the number
			}
			case BinaryOperationExpression bin -> {
				switch (bin.operator()) {
					case SET -> {
						switch (bin.left()) {
							case AccessExpression access -> fieldSet(bin.right(), access, builder, context);
							case VariableExpression variable -> variableSet(variable, bin.right(), builder, context);
							case null, default -> throw new UnsupportedOperationException();
						}
					}
					case NULL_COALESCE -> throw new NotImplementedException();
					case CONDITIONAL -> {
						writeExpression(bin.left(), builder, context, float.class);

						builder.ifThen(
							Opcode.IFEQ,
							eq -> writeExpression(bin.right(), builder, context, expectedType)
						);
					}
					case LOGICAL_OR -> throw new NotImplementedException();
					case LOGICAL_AND -> throw new NotImplementedException();
					case EQUAL_TO -> throw new NotImplementedException();
					case NOT_EQUAL -> throw new NotImplementedException();
					case LESS_THAN -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fcmpl();
					}
					case GREATER_THAN -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fcmpg();
					}
					case LESS_THAN_OR_EQUAL_TO -> throw new NotImplementedException();
					case GREATER_THAN_OR_EQUAL_TO -> throw new NotImplementedException();
					case ADD -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fadd();
					}
					case SUBTRACT -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fsub();
					}
					case MULTIPLY -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fmul();
					}
					case DIVIDE -> {
						writeExpression(bin.left(), builder, context, float.class); // push left
						writeExpression(bin.right(), builder, context, float.class); // push right

						builder.fdiv();
					}
					case ARROW -> throw new NotImplementedException();
				}
			}
			case UnaryOperationExpression unary -> {
				switch (unary.operator()) {
					case NEGATE -> {
						writeExpression(unary.value(), builder, context, float.class);
						builder.fneg();
					}
					case LOGICAL_NEGATE -> throw new NotImplementedException();
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
				builder.ifThenElse(
					Opcode.IFEQ,
					eq -> writeExpression(ternary.ifTrue(), eq, context, expectedType),
					ne -> writeExpression(ternary.ifFalse(), ne, context, expectedType)
				);
			}
			case ArrayAccessExpression arrayAccess -> {
				writeExpression(arrayAccess.array(), builder, context, expectedType.arrayType());
				writeArrayIndex(builder, context, arrayAccess);

				builder.arrayLoadInstruction(kindOf(expectedType));
			}
			case KeywordExpression keyword -> throw new NotImplementedException();
			case StringExpression string -> {
				if (!expectedType.isAssignableFrom(String.class)) {
					throw new IllegalStateException("Expected: " + expectedType + ", Got: String");
				}

				builder.ldc(builder.constantPool().stringEntry(string.value()));
			}
		}
	}

	private void writeArrayIndex(CodeBuilder builder, CompileContext context, ArrayAccessExpression arrayAccess) {
		if (arrayAccess.index() instanceof NumberExpression(float value)) {
			builder.ldc(builder.constantPool().intEntry((int) value));
		} else {
			writeExpression(arrayAccess.index(), builder, context, float.class);
			builder.f2i();
		}
	}

	private static TypeKind kindOf(Class<?> clazz) {
		return TypeKind.fromDescriptor(clazz.descriptorString());
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
