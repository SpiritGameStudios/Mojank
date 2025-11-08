package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.ast.VariableExpression;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Parser;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.analysis.Analyser;
import dev.spiritstudios.mojank.meow.binding.Alias;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.TypeKind;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
	private final Parser parser;
	private final Method targetMethod;

	private final Map<String, IndexedParameter> parameters;
	private final int variablesIndex;

	private final Map<String, Class<?>> variables = new HashMap<>();

	private final Deferred<MethodHandles.Lookup> deferredLookup = new Deferred<>();

	private final ClassDesc compiledDesc;
	private final ClassDesc variableDesc;

	Compiler(
		final MethodHandles.Lookup lookup,
		final Class<T> type,
		final Linker linker,
		final Parser parser
	) {
		this.lookup = lookup;
		this.type = type;
		this.linker = linker;
		this.parser = parser;
		this.targetMethod = linker.tryFunctionalClass(type)
			.orElseThrow(() -> new IllegalArgumentException("clazz " + this.type + " has no suitable methods"));

		final var pack = lookup.lookupClass().getPackage().getName();
		this.compiledDesc = ClassDesc.of(pack, "\uD83C\uDFF3️\u200D⚧️️" + this.type.getSimpleName());
		this.variableDesc = ClassDesc.of(pack, "☃" + this.type.getSimpleName() + "Variables");

		this.parameters = new Object2ObjectOpenHashMap<>(targetMethod.getParameterCount());

		var methodParams = targetMethod.getParameters();
		for (int i = 0; i < methodParams.length; i++) {
			var param = methodParams[i];

			final var alias = param.getAnnotation(Alias.class);
			if (alias == null) {
				continue;
			}

			var indexed = new IndexedParameter(param, i);
			for (final var str : alias.value()) {
				parameters.put(str, indexed);
			}
		}
		this.variablesIndex = methodParams.length;
	}

	/**
	 * Finalises the compiler, stopping all future compilations, and allows the resulting classes to function.
	 */
	public Supplier<Variables> finish() {
		try {
			final byte[] bytes = BoilerplateGenerator.writeVariablesClass(this.variableDesc, this.variables);

			DebugUtils.decompile(bytes);
			DebugUtils.javap(bytes);

			final MethodHandles.Lookup lookup = this.lookup.defineHiddenClass(bytes, true);

			// The lock is set.
			this.deferredLookup.value = lookup;

			final var constructor = lookup.findConstructor(lookup.lookupClass(), MethodType.methodType(void.class));

			// The LambdaMetafactory was beating me up, so have a plain ol' lambda instead.
			// Probably could be tricked by making the resulting Variables class return its own constructor supplier.
			return () -> {
				try {
					return (Variables) constructor.invoke();
				} catch (Throwable throwable) {
					throw new AssertionError(throwable);
				}
			};
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}

	public byte[] compile(String program) {
		var parsed = parser.parse(program);
		Analyser analyser = new Analyser(parameters, linker);
		logger.info(program);
		logger.info(parsed.toString());
		logger.info(analyser.evalType(parsed).toString());
		return compileToBytes(program, parsed);
	}

	public T compileAndInitialize(String program) {
		if (this.deferredLookup.isPresent()) {
			throw new IllegalStateException("Compiler has been finalised.");
		}

		return (T) compileToResult(program);
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
		final String program
	) {
		final var bytes = compile(program);

		DebugUtils.decompile(bytes);
//		DebugUtils.javap(bytes);

		try {
			final var result = this.lookup.defineHiddenClassWithClassData(bytes, deferredLookup, true);

			//noinspection unchecked
			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	private void compile(ClassBuilder builder, Expression expression) {
		builder.withMethod(
			targetMethod.getName(),
			methodDesc(targetMethod.getReturnType(), targetMethod.getParameterTypes()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			mb -> {
				mb.withCode(cob -> {
					writeExpression(expression, cob);
					cob.freturn();
				});
			}
		);
	}

	private void localGet(AccessExpression access, CodeBuilder builder) {
		var name = nameOf(access.fields());

//		int index = locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
//		builder.fload(index);
	}

	private void localSet(AccessExpression access, CodeBuilder builder) {
		var name = nameOf(access.fields());

//		int index = locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
//		builder.fstore(index);
	}

	private void fieldSet(Expression setTo, AccessExpression access, CodeBuilder builder) {
		var first = access.first();

		if (linker.isLocal(first)) {
			resolveFloat(setTo, builder);
			localSet(access, builder);
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
				resolveFloat(setTo, builder);
				put = true;
			}

			if (Modifier.isStatic(fieldMods)) {
				builder.fieldInstruction(
					put ? Opcode.PUTSTATIC : Opcode.GETSTATIC,
					builder.constantPool().fieldRefEntry(
						desc(fieldType),
						fieldName,
						desc(newField.getType())
					)
				);
			} else {
				builder.fieldInstruction(
					put ? Opcode.PUTFIELD : Opcode.GETFIELD,
					desc(fieldType),
					fieldName,
					desc(newField.getType())
				);
			}

			fieldType = newField.getType();
		}
	}

	private void createVariableIfAbsent(String name) {
		// TODO: consider objects
		this.variables.putIfAbsent(name, float.class);
	}

	private void variableGet(VariableExpression variable, CodeBuilder builder) {
		var name = nameOf(variable.fields());

		createVariableIfAbsent(name);

		builder
			.aload(variablesIndex)
			.invokeDynamicInstruction(
				DynamicCallSiteDesc.of(
					MethodHandleDesc.ofMethod(
						DirectMethodHandleDesc.Kind.STATIC,
						desc(MeowBootstraps.class),
						"getter",
						methodDesc(
							CallSite.class,
							MethodHandles.Lookup.class,
							String.class,
							MethodType.class
						)
					),
					name,
					methodDesc(
						float.class,
						Object.class
					)
				)
			);
	}

	private void variableSet(
		VariableExpression variable,
		Expression setTo,
		CodeBuilder builder
	) {
		var name = nameOf(variable.fields());

		createVariableIfAbsent(name);

		builder.aload(variablesIndex);

		resolveFloat(setTo, builder);

		builder.invokeDynamicInstruction(
			DynamicCallSiteDesc.of(
				MethodHandleDesc.ofMethod(
					DirectMethodHandleDesc.Kind.STATIC,
					desc(MeowBootstraps.class),
					"setter",
					methodDesc(
						CallSite.class,
						MethodHandles.Lookup.class,
						String.class,
						MethodType.class
					)
				),
				name,
				methodDesc(
					void.class,
					Variables.class,
					float.class
				)
			)
		);
	}

	private void fieldGet(AccessExpression access, CodeBuilder builder) {
		var first = access.first();

		if (Linker.isLocal(first)) {
			localGet(access, builder);
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

	private void functionCall(FunctionCallExpression functionCall,
							  CodeBuilder builder,
							  Primitives expected
	) {
		if (!(functionCall.function() instanceof AccessExpression access)) {
			throw new RuntimeException();
		}

		var method = linker.findMethod(access);
		if (!expected.isCompatibleTarget(method.getReturnType())) {
			throw new IllegalStateException("uwu you fucked up your return types meow");
		}

		var paramTypes = method.getParameterTypes();
		var mods = method.getModifiers();

		if (Modifier.isStatic(mods)) {
			List<Expression> arguments = functionCall.arguments();

			for (int i = 0; i < arguments.size(); i++) {
				Expression argument = arguments.get(i);
				Class<?> type = paramTypes[i];

				if (Primitives.Float.isCompatibleTarget(type)) {
					resolveFloat(argument, builder);
				} else if (type.isAssignableFrom(String.class)) {
					resolveString(argument, builder);
				} else {
					throw new UnsupportedOperationException();
				}
			}

			builder.invokestatic(
				desc(method.getDeclaringClass()),
				method.getName(),
				methodDesc(method.getReturnType(), method.getParameterTypes()),
				method.getDeclaringClass().isInterface()
			);
		} else {

		}
	}

	private void resolveFloat(Expression expression, CodeBuilder builder) {
		switch (expression) {
			case AccessExpression access -> {
				fieldGet(access, builder);
			}
			case VariableExpression variable -> {
				variableGet(variable, builder);
			}
			case FunctionCallExpression functionCall -> {
				functionCall(functionCall, builder, Primitives.Float);
			}
			case NumberExpression num -> {
				builder.constantInstruction(num.value()); // push the number
			}
			case BinaryOperationExpression bin -> {
				switch (bin.operator()) {
					case SET -> {
						switch (bin.left()) {
							case AccessExpression access -> fieldSet(bin.right(), access, builder);
							case VariableExpression variable -> variableSet(variable, bin.right(), builder);
							case null, default -> throw new UnsupportedOperationException();
						}
					}
					case NULL_COALESCE -> {
					}
					case CONDITIONAL -> {
						writeExpression(bin.left(), builder);

						builder.ifThen(
							Opcode.IFEQ,
							eq -> writeExpression(bin.right(), builder)
						);
					}
					case LOGICAL_OR -> {
					}
					case LOGICAL_AND -> {
					}
					case EQUAL_TO -> {

					}
					case NOT_EQUAL -> {
					}
					case LESS_THAN -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fcmpl();
					}
					case GREATER_THAN -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fcmpg();
					}
					case LESS_THAN_OR_EQUAL_TO -> {
					}
					case GREATER_THAN_OR_EQUAL_TO -> {
					}
					case ADD -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fadd();
					}
					case SUBTRACT -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fsub();
					}
					case MULTIPLY -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fmul();
					}
					case DIVIDE -> {
						resolveFloat(bin.left(), builder); // push left
						resolveFloat(bin.right(), builder); // push right

						builder.fdiv();
					}
					case ARROW -> {
					}
				}
			}
			case UnaryOperationExpression unary -> {
				switch (unary.operator()) {
					case NEGATE -> {
						resolveFloat(unary.value(), builder);
						builder.fneg();
					}
					case LOGICAL_NEGATE -> {

					}
					case RETURN -> {
						resolveFloat(unary.value(), builder);
					}
				}
			}
			case TernaryOperationExpression ternary -> {
				builder.ifThenElse(
					Opcode.IFEQ,
					eq -> resolveFloat(ternary.ifTrue(), eq),
					ne -> resolveFloat(ternary.ifFalse(), ne)
				);
			}
			default -> {
				throw new UnsupportedOperationException("Not a float: " + expression.toStr());
			}
		}
	}

	private void resolveString(Expression expression, CodeBuilder builder) {
		switch (expression) {
			case StringExpression str -> {
				builder.ldc(builder.constantPool().stringEntry(str.value()));
			}
			case FunctionCallExpression func -> {
				functionCall(func, builder, Primitives.Unknown);
			}

			default -> throw new UnsupportedOperationException();
		}
	}


	private void writeExpression(Expression primitive, CodeBuilder builder) {
		switch (primitive) {
			case ComplexExpression expression -> {
				for (Expression subExpression : expression.expressions()) {
					writeExpression(subExpression, builder);
				}
			}
			case FunctionCallExpression expression -> {
				functionCall(expression, builder, Primitives.Float);
			}
			default -> {
				resolveFloat(primitive, builder);
			}
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
