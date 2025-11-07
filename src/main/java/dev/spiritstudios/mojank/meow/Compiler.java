package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.ReturnExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.Util;
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

import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.methodDesc;
import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.writeStub;

/**
 * @author Ampflower
 **/
public final class Compiler<T> {
	public static final ClassDesc DESCRIPTOR = Compiler.class.describeConstable().orElseThrow();

	private static final Logger logger = Util.logger();

	private final MethodHandles.Lookup lookup;
	private final Linker linker;
	private final Class<T> type;
	private final Parser parser;
	private final Method targetMethod;

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
		return compileToBytes(program, parser.parse(program));
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
		logger.info(expression.toString());

		// Find the method we are going to override with our compile result

		return ClassFile.of().build(
			compiledDesc,
			builder -> {
// Write the constructor and a few misc functions (toString, hashCode, etc.)

				writeStub(compiledDesc, type, targetMethod, program, builder);

				var context = new CompileContext(targetMethod, compiledDesc, builder);
				compile(builder, context, expression);
			}
		);
	}

	private CompilerResult<T> compileToResult(
		final String program
	) {
		final var bytes = compile(program);

		DebugUtils.decompile(bytes);
		DebugUtils.javap(bytes);

		try {
			final var result = this.lookup.defineHiddenClassWithClassData(bytes, deferredLookup, true);

			//noinspection unchecked
			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	private void compile(ClassBuilder builder, CompileContext context, Expression expression) {
		builder.withMethod(
			targetMethod.getName(),
			methodDesc(targetMethod.getReturnType(), targetMethod.getParameterTypes()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			mb -> mb.withCode(cob -> {
				writeExpression(expression, context, cob);
				cob.freturn();
			})
		);
	}

	private void localGet(AccessExpression access, CompileContext context, CodeBuilder builder) {
		var name = nameOf(access);

		int index = context.locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
		builder.fload(index);
	}

	private void localSet(AccessExpression access, CompileContext context, CodeBuilder builder) {
		var name = nameOf(access);

		int index = context.locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
		builder.fstore(index);
	}

	private void fieldSet(Expression setTo, AccessExpression access, CompileContext context, CodeBuilder builder) {
		var first = access.first();

		if (linker.isLocal(first)) {
			resolveFloat(setTo, context, builder);
			localSet(access, context, builder);
			return;
		}

		if (linker.isVariable(first)) {
			variableSet(setTo, access, context, builder);
			return;
		}

		var param = context.parameters.get(first);

		Class<?> fieldType;
		int fieldMods = 0;
		String fieldName = "";

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
				resolveFloat(setTo, context, builder);
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

	private void variableGet(AccessExpression access, CompileContext context, CodeBuilder builder) {
		var name = nameOf(access);

		createVariableIfAbsent(name);

		builder
			.aload(context.variablesIndex)
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

	private void variableSet(Expression setTo, AccessExpression access, CompileContext context, CodeBuilder builder) {
		var name = nameOf(access);

		createVariableIfAbsent(name);

		builder.aload(context.variablesIndex);

		resolveFloat(setTo, context, builder);

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

	private void fieldGet(AccessExpression access, CompileContext context, CodeBuilder builder) {
		var first = access.first();

		if (linker.isLocal(first)) {
			localGet(access, context, builder);
			return;
		}

		if (linker.isVariable(first)) {
			variableGet(access, context, builder);
			return;
		}

		var param = context.parameters.get(first);

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
							  CompileContext context,
							  CodeBuilder builder,
							  Primitives expected) {
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
					resolveFloat(argument, context, builder);
				} else if (type.isAssignableFrom(String.class)) {
					resolveString(argument, context, builder);
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

	private void resolveFloat(Expression expression, CompileContext context, CodeBuilder builder) {
		switch (expression) {
			case AccessExpression access -> {
				fieldGet(access, context, builder);
			}
			case FunctionCallExpression functionCall -> {
				functionCall(functionCall, context, builder, Primitives.Float);
			}
			case NumberExpression num -> {
				builder.constantInstruction(num.value()); // push the number
			}
			case BinaryOperationExpression bin -> {
				switch (bin.operator()) {
					case SET -> {
						if (!(bin.left() instanceof AccessExpression access))
							throw new UnsupportedOperationException();

						fieldSet(bin.right(), access, context, builder);
					}
					case NULL_COALESCE -> {
					}
					case CONDITIONAL -> {
						writeExpression(bin.left(), context, builder);

						builder.ifThen(
							Opcode.IFEQ,
							eq -> writeExpression(bin.right(), context, builder)
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
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fcmpl();
					}
					case GREATER_THAN -> {
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fcmpg();
					}
					case LESS_THAN_OR_EQUAL_TO -> {
					}
					case GREATER_THAN_OR_EQUAL_TO -> {
					}
					case ADD -> {
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fadd();
					}
					case SUBTRACT -> {
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fsub();
					}
					case MULTIPLY -> {
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fmul();
					}
					case DIVIDE -> {
						resolveFloat(bin.left(), context, builder); // push left
						resolveFloat(bin.right(), context, builder); // push right

						builder.fdiv();
					}
					case ARROW -> {
					}
				}
			}
			case UnaryOperationExpression unary -> {
				switch (unary.operator()) {
					case NEGATE -> {
						resolveFloat(unary.value(), context, builder);
						builder.fneg();
					}
					case LOGICAL_NEGATE -> {

					}
				}
			}
			case TernaryOperationExpression ternary -> {
				builder.ifThenElse(
					Opcode.IFEQ,
					eq -> resolveFloat(ternary.ifTrue(), context, eq),
					ne -> resolveFloat(ternary.ifFalse(), context, ne)
				);
			}
			default -> {
				throw new UnsupportedOperationException("Not a float: " + expression.toStr());
			}
		}
	}

	private void resolveString(Expression expression, CompileContext context, CodeBuilder builder) {
		switch (expression) {
			case StringExpression str -> {
				builder.ldc(builder.constantPool().stringEntry(str.value()));
			}
			case FunctionCallExpression func -> {
				functionCall(func, context, builder, Primitives.Unknown);
			}

			default -> throw new UnsupportedOperationException();
		}
	}


	private void writeExpression(Expression primitive, CompileContext context, CodeBuilder builder) {
		switch (primitive) {
			case ComplexExpression expression -> {
				for (Expression subExpression : expression.expressions()) {
					writeExpression(subExpression, context, builder);
				}
			}
			case FunctionCallExpression expression -> {
				functionCall(expression, context, builder, Primitives.Float);
			}
			case ReturnExpression ret -> {
				writeExpression(ret.value(), context, builder);
			}
			default -> {
				resolveFloat(primitive, context, builder);
			}
		}
	}

	private static String nameOf(AccessExpression access) {
		StringBuilder builder = new StringBuilder();
		builder.append(access.first());

		for (String field : access.fields()) {
			builder.append(field).append("$");
		}

		builder.deleteCharAt(builder.length() - 1);

		return builder.toString();
	}
}
