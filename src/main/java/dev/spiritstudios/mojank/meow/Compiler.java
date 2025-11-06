package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.IdentifierExpression;
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
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.methodDesc;
import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.writeStub;

/**
 * @author Ampflower
 **/
public sealed abstract class Compiler<T> permits MolangCompiler {
	public static final ClassDesc DESCRIPTOR = Compiler.class.describeConstable().orElseThrow();

	private static final Logger logger = Util.logger();

	private final MethodHandles.Lookup lookup;
	protected final Linker linker;
	protected final Class<T> type;
	protected final Method targetMethod;
	protected final ClassBuilder variablesBuilder;
	protected final Set<String> definedVariables = new HashSet<>();

	protected final ClassDesc compiledDesc;

	protected Compiler(
		final MethodHandles.Lookup lookup,
		final Class<T> type,
		final Linker linker,
		ClassBuilder variablesBuilder
	) {
		this.lookup = lookup;
		this.type = type;
		this.linker = linker;
		this.targetMethod = linker.tryFunctionalClass(type)
			.orElseThrow(() -> new IllegalArgumentException("clazz " + this.type + " has no suitable methods"));

		this.variablesBuilder = variablesBuilder;

		final var pack = lookup.lookupClass().getPackage().getName();
		this.compiledDesc = ClassDesc.of(pack + ".\uD83C\uDFF3️\u200D⚧️️" + this.type.getSimpleName());
	}

	public abstract byte[] compile(String program);

	protected final byte[] compileToBytes(
		final String program,
		final Expression expression
	) {
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

	protected final CompilerResult<T> compile(
		final String program,
		final Expression expression
	) {
		logger.info(expression.toStr());

		final var bytes = compileToBytes(program, expression);

		DebugUtils.decompile(bytes);
//		DebugUtils.javap(bytes);

		try {
			final var result = this.lookup.defineHiddenClassWithClassData(bytes, this, true);

			//noinspection unchecked
			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	protected final void compile(ClassBuilder builder, CompileContext context, Expression expression) {
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

	private Class<?> resolveClass(IdentifierExpression id) {
		return this.linker
			.findClass(id.value())
			.orElseThrow(() -> new UnsupportedOperationException("field: " + id.value()));
	}

	private Class<?> resolveClass(AccessExpression access) {
		if (access.object() instanceof AccessExpression objectAccess) return resolveClass(objectAccess);
		else if (access.object() instanceof IdentifierExpression id) {
			return resolveClass(id);
		} else {
			throw new UnsupportedOperationException("not an access or id: " + access.object().toStr());
		}
	}

	private void localGet(String name, CompileContext context, CodeBuilder builder) {
		int index = context.locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
		builder.fload(index);
	}

	private void localSet(String name, CompileContext context, CodeBuilder builder) {
		int index = context.locals.computeIfAbsent(name, k -> builder.allocateLocal(TypeKind.FloatType));
		builder.fstore(index);
	}

	private void fieldSet(Expression setTo, AccessExpression access, CompileContext context, CodeBuilder builder) {
		if (access.object() instanceof IdentifierExpression id) {
			if (id.value().equalsIgnoreCase("temp")) {
				resolveFloat(setTo, context, builder);
				localSet(access.toAccess(), context, builder);
				return;
			}

			if (id.value().equalsIgnoreCase("variable")) {
				variableSet(setTo, access.toAccess(), context, builder);
				return;
			}

			resolveFloat(setTo, context, builder);

			var param = context.parameters.get(id.value());
			if (param != null) {
				builder.loadInstruction(
					TypeKind.ReferenceType,
					builder.parameterSlot(param.index())
				);

				var field = linker.findField(param.parameter().getType(), access.toAccess());

				builder.fieldInstruction(
					Opcode.PUTFIELD,
					desc(param.parameter().getType()),
					field.getName(),
					desc(field.getType())
				);

				return;
			}
		}

		var clazz = resolveClass(access);
		var field = linker.findField(clazz, access.toAccess());
		var mods = field.getModifiers();
		if (!Primitives.Float.isCompatibleTarget(field.getType())) {
			throw new RuntimeException("not a float");
		}

		if (Modifier.isStatic(mods)) {
			builder.fieldInstruction(
				Opcode.PUTSTATIC,
				builder.constantPool().fieldRefEntry(
					desc(clazz),
					field.getName(),
					desc(field.getType())
				)
			);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private void createVariableIfAbsent(String name) {
		if (definedVariables.contains(name)) return;

		variablesBuilder.withField(name, ConstantDescs.CD_float, ClassFile.ACC_PUBLIC);

		definedVariables.add(name);
	}

	private void variableGet(String name, CompileContext context, CodeBuilder builder) {
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
							MethodType.class,
							Class.class
						)
					),
					name,
					methodDesc(
						float.class,
						Object.class
					),
					DynamicConstantDesc.ofNamed(
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.STATIC,
							desc(MethodHandles.class),
							"classData",
							MethodTypeDesc.of(
								ConstantDescs.CD_Object,
								desc(MethodHandles.Lookup.class),
								ConstantDescs.CD_String,
								ConstantDescs.CD_Class
							)
						),
						ConstantDescs.DEFAULT_NAME,
						ConstantDescs.CD_Class
					)
				)
			);
	}

	private void variableSet(Expression setTo, String name, CompileContext context, CodeBuilder builder) {
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
						MethodType.class,
						Class.class
					)
				),
				name,
				methodDesc(
					void.class,
					Variables.class,
					float.class
				),
				DynamicConstantDesc.ofNamed(
					MethodHandleDesc.ofMethod(
						DirectMethodHandleDesc.Kind.STATIC,
						desc(MethodHandles.class),
						"classData",
						MethodTypeDesc.of(
							ConstantDescs.CD_Object,
							desc(MethodHandles.Lookup.class),
							ConstantDescs.CD_String,
							ConstantDescs.CD_Class
						)
					),
					ConstantDescs.DEFAULT_NAME,
					ConstantDescs.CD_Class
				)
			)
		);
	}

	private void fieldGet(AccessExpression access, CompileContext context, CodeBuilder builder) {
		if (access.object() instanceof IdentifierExpression id) {
			if (id.value().equalsIgnoreCase("temp")) {
				localGet(access.toAccess(), context, builder);
				return;
			}

			if (id.value().equalsIgnoreCase("variable")) {
				variableGet(access.toAccess(), context, builder);
				return;
			}

			var param = context.parameters.get(id.value());
			if (param != null) {
				builder.loadInstruction(
					TypeKind.ReferenceType,
					builder.parameterSlot(param.index())
				);

				var field = linker.findField(param.parameter().getType(), access.toAccess());

				builder.fieldInstruction(
					Opcode.GETFIELD,
					desc(param.parameter().getType()),
					field.getName(),
					desc(field.getType())
				);

				return;
			}
		}

		var clazz = resolveClass(access);
		var field = linker.findField(clazz, access.toAccess());
		var mods = field.getModifiers();
		if (!Primitives.Float.isCompatibleTarget(field.getType())) {
			throw new RuntimeException("not a float");
		}

		if (Modifier.isStatic(mods)) {
			builder.fieldInstruction(
				Opcode.GETSTATIC,
				builder.constantPool().fieldRefEntry(
					desc(clazz),
					field.getName(),
					desc(field.getType())
				)
			);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private void functionCall(FunctionCallExpression functionCall,
							  CompileContext context,
							  CodeBuilder builder,
							  Primitives expected) {
		if (!(functionCall.function() instanceof AccessExpression access)) {
			throw new RuntimeException();
		}

		Class<?> clazz = resolveClass(access);
		var method = linker.findMethod(clazz, access.toAccess());
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
				desc(clazz),
				method.getName(),
				methodDesc(method.getReturnType(), method.getParameterTypes()),
				clazz.isInterface()
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


	protected final void writeExpression(Expression primitive, CompileContext context, CodeBuilder builder) {
		var constPool = builder.constantPool();

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

//	private static Operation compile(
//		final Linker linker,
//		final Context context,
//		final Expression primitive
//	) throws Throwable {
//		try {
//			return switch (primitive) {
//				case AccessExpression exp -> {
//					final var insns = new MethodNode();
//
//					final Field field;
//					final Operation op;
//					final Protoparameter param;
//
//					if (exp.object() instanceof IdentifierExpression(String value)
//						&& (param = context.localTable().get(value)) != null
//					) {
//						final var get = new MethodNode();
//						Jit.visitLoad(get, param.type, param.index);
//						op = new Operation(0, param.type, null, get);
//						field = linker.findField(param.type, exp.toAccess());
//					} else {
//						logger.debug("{} => {}", exp, context);
//
//						op = compile(linker, context, exp.object());
//						field = linker.findField(op.top(), exp.toAccess());
//					}
//
//					Jit.visitFieldGet(insns, field);
//
//					if (Modifier.isStatic(field.getModifiers())) {
//						// Field.getType will always return a class with the same type of Field.get, cast is safe
//						//noinspection unchecked
//						yield new Operation(
//							0,
//							(Class<Object>) field.getType(),
//							Optional.ofNullable(field.get(null)),
//							insns
//						);
//					} else {
//						yield op.combine(1, field.getType(), Optional.empty(), insns);
//					}
//				}
//				case ArrayAccessExpression exp -> {
//					final var array = compile(linker, context, exp.array());
//					final var field = compile(linker, context, exp.array());
//
//					throw new UnsupportedOperationException();
//				}
//				case BinaryOperationExpression exp -> {
//					final var left = compile(linker, context, exp.left());
//					final var right = compile(linker, context, exp.right());
//
//					if (left.push().size() != 1 || left.pop() != 0) {
//						throw new IllegalStateException("left (" + left + ") must be at stack 1: " + exp.left());
//					}
//
//					if (right.push().size() != 1 || right.pop() != 0) {
//						throw new IllegalStateException("right (" + right + ") must be at stack 1: " + exp.right());
//					}
//
//					final var insns = new MethodNode();
//					Class<?> clazz = null;
//
//					switch (exp.operator()) {
//						case SET -> {
//							throw new UnsupportedOperationException();
//						}
//						case NULL_COALESCE -> {
//							throw new UnsupportedOperationException();
//						}
//						case CONDITIONAL -> {
//							throw new UnsupportedOperationException();
//						}
//						case LOGICAL_OR -> {
//							throw new UnsupportedOperationException();
//						}
//						case LOGICAL_AND -> {
//							throw new UnsupportedOperationException();
//						}
//						case EQUAL_TO -> {
//							throw new UnsupportedOperationException();
//						}
//						case NOT_EQUAL -> {
//							throw new UnsupportedOperationException();
//						}
//						case LESS_THAN -> {
//							throw new UnsupportedOperationException();
//						}
//						case GREATER_THAN -> {
//							throw new UnsupportedOperationException();
//						}
//						case LESS_THAN_OR_EQUAL_TO -> {
//							throw new UnsupportedOperationException();
//						}
//						case GREATER_THAN_OR_EQUAL_TO -> {
//							throw new UnsupportedOperationException();
//						}
//						case ADD -> {
//							// FIXME: boxing
//							left.instructions.accept(insns);
//							Jit.visitCoerce(insns, left.top(), float.class);
//							right.instructions.accept(insns);
//							Jit.visitCoerce(insns, right.top(), float.class);
//							insns.visitInsn(Opcodes.FADD);
//							clazz = float.class;
//						}
//						case SUBTRACT -> {
//							// FIXME: boxing
//							left.instructions.accept(insns);
//							Jit.visitCoerce(insns, left.top(), float.class);
//							right.instructions.accept(insns);
//							Jit.visitCoerce(insns, right.top(), float.class);
//							insns.visitInsn(Opcodes.FSUB);
//							clazz = float.class;
//						}
//						case MULTIPLY -> {
//							// FIXME: boxing
//							left.instructions.accept(insns);
//							Jit.visitCoerce(insns, left.top(), float.class);
//							right.instructions.accept(insns);
//							Jit.visitCoerce(insns, right.top(), float.class);
//							insns.visitInsn(Opcodes.FMUL);
//							clazz = float.class;
//						}
//						case DIVIDE -> {
//							// FIXME: boxing
//							left.instructions.accept(insns);
//							Jit.visitCoerce(insns, left.top(), float.class);
//							right.instructions.accept(insns);
//							Jit.visitCoerce(insns, right.top(), float.class);
//							insns.visitInsn(Opcodes.FDIV);
//							clazz = float.class;
//						}
//						case ARROW -> {
//							throw new UnsupportedOperationException();
//						}
//					}
//
//					yield new Operation(0, clazz, null, insns);
//				}
//				case BreakExpression exp -> {
//					throw new UnsupportedOperationException();
//				}
//				case ComplexExpression exp -> {
//					Operation op = new Operation();
//
//					for (final var e : exp.expressions()) {
//						op = op.combine(compile(linker, context, e));
//					}
//
//					yield op;
//				}
//				case ContinueExpression exp -> {
//					throw new UnsupportedOperationException();
//				}
//				case FunctionCallExpression exp -> {
//					if (!(exp.function() instanceof AccessExpression(Expression object, String toAccess))) {
//						throw new UnsupportedOperationException("not an access: " + exp.function());
//					}
//
//					final var args = new Operation[exp.arguments().size()];
//					final var cargs = new Class<?>[args.length];
//
//					for (int i = 0; i < args.length; i++) {
//						final var argExp = exp.arguments().get(i);
//						final var arg = compile(linker, context, argExp);
//
//						if (arg.pop != 0) {
//							throw new IllegalArgumentException("argument " + argExp + " pops: " + arg);
//						}
//
//						if (arg.push.size() != 1) {
//							throw new IllegalArgumentException("argument " + argExp + " fails to push single entry: " + arg);
//						}
//
//						args[i] = arg;
//						cargs[i] = arg.top();
//					}
//
//					final var insns = new MethodNode();
//
//					final var funcBase = compile(linker, context, object);
//					final var method = linker.findMethod(funcBase.top(), toAccess, cargs);
//
//					final var modifiers = method.getModifiers();
//					final boolean isInterface;
//					final int invokeOpcode;
//
//					if (Modifier.isStatic(modifiers)) {
//						isInterface = false;
//						invokeOpcode = Opcodes.INVOKESTATIC;
//					} else {
//						funcBase.instructions.accept(insns);
//						isInterface = method.getDeclaringClass().isInterface();
//						invokeOpcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
//					}
//
//					final var params = method.getParameterTypes();
//
//					for (int i = 0; i < params.length; i++) {
//						args[i].instructions.accept(insns);
//						Jit.visitCoerce(insns, cargs[i], params[i]);
//					}
//
//					insns.visitMethodInsn(
//						invokeOpcode,
//						Type.getInternalName(method.getDeclaringClass()),
//						method.getName(),
//						Type.getMethodDescriptor(method),
//						isInterface
//					);
//
//					yield new Operation(0, (Class) method.getReturnType(), null, insns);
//				}
//				case IdentifierExpression exp -> {
//					final Pair<Class<?>, ?> pair = switch (exp.value().toLowerCase()) {
//						case "math" -> Pair.of(Math.class, Math.class);
//						default -> throw new UnsupportedOperationException("field: " + exp.value());
//					};
//					yield new Operation(0, (Class) pair.key(), Optional.ofNullable(pair.value()), new InsnList());
//				}
//				case NumberExpression exp -> {
//					final var insns = new MethodNode();
//					if (exp == NumberExpression.ZERO) {
//						insns.visitInsn(Opcodes.FCONST_0);
//					} else if (exp == NumberExpression.ONE) {
//						insns.visitInsn(Opcodes.FCONST_1);
//					} else {
//						Jit.visitFloat(insns, exp.value());
//					}
//					yield new Operation(0, float.class, exp.value(), insns);
//				}
//				case ReturnExpression exp -> {
//					final var value = compile(linker, context, exp.value());
//
//					final var signature = context.signature();
//					if (!signature.returnType().isAssignableFrom(value.top())) {
//						throw new IllegalArgumentException(value + " != " + signature.returnType());
//					}
//
//					final var insns = new InsnList();
//					insns.insert(new InsnNode(Primitives.returnOpcodeOf(signature.returnType())));
//					yield value.combine(1, List.of(), List.of(), insns);
//				}
//				case StringExpression exp -> {
//					final var insns = new InsnList();
//					insns.add(new LdcInsnNode(exp.value()));
//					yield new Operation(0, String.class, exp.value(), insns);
//				}
//				case TernaryOperationExpression exp -> {
//					throw new UnsupportedOperationException();
//				}
//				case UnaryOperationExpression exp -> {
//					var right = compile(linker, context, exp.value());
//
//					switch (exp.operator()) {
//						case NEGATE -> {
//							if (right.push.size() != 1) {
//								throw new IllegalStateException("Target of negation has multiple or no values on the stack.");
//							}
//
//							final var insns = new MethodNode();
//							Jit.visitCoerce(insns, right.top(), float.class);
//							insns.visitInsn(Opcodes.FNEG);
//
//							yield right.combine(
//								1,
//								float.class,
//								Optional.empty(),
//								insns
//							);
//						}
//						case LOGICAL_NEGATE -> {
//						}
//					}
//
//					throw new UnsupportedOperationException();
//				}
//			};
//		} catch (UnsupportedOperationException e) {
//			throw new UnsupportedOperationException("unsupported: " + primitive.toStr(), e);
//		} catch (Throwable t) {
//			throw new IllegalArgumentException("expr: " + primitive.toStr(), t);
//		}
//	}
//
//	private record Context(
//		MethodType signature,
//		Map<String, Protoparameter> localTable
//	) {
//		Context(
//			final Method method
//		) {
//			this(
//				MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
//				toLocalTable(method)
//			);
//		}
//
//		// TODO: find a better spot for this?
//		private static Map<String, Protoparameter> toLocalTable(final Method method) {
//			final var map = new HashMap<String, Protoparameter>();
//			final var params = method.getParameters();
//			int offset = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
//
//			for (int i = 0; i < params.length; i++) {
//				final var proto = new Protoparameter(i + offset, params[i].getType());
//				if (proto.type == long.class || proto.type == double.class) {
//					// 2 slots
//					offset++;
//				}
//				final var alias = params[i].getAnnotation(Alias.class);
//				if (alias == null) {
//					continue;
//				}
//				for (final var str : alias.value()) {
//					map.put(str, proto);
//				}
//			}
//
//			return Map.copyOf(map);
//		}
//	}
//
//	private record Protoparameter(
//		int index,
//		Class<?> type
//	) {
//	}
//
//
//	/**
//	 * Bytecode instructions and information about the state of the stack after running them.
//	 */
//	private record Operation(
//		int pop,
//		List<Class<?>> push,
//		List<Optional<?>> raws,
//		InsnList instructions
//	) {
//		<T> Operation(
//			final int pop,
//			final Class<T> push,
//			final @Nullable T raws,
//			final MethodNode insns
//		) {
//			this(pop, List.of(push), List.of(Optional.ofNullable(raws)), insns.instructions);
//		}
//
//		<T> Operation(
//			final int pop,
//			final Class<T> push,
//			final @Nullable T raws,
//			final InsnList insns
//		) {
//			this(pop, List.of(push), List.of(Optional.ofNullable(raws)), insns);
//		}
//
//		Operation() {
//			this(0, List.of(), List.of(), new InsnList());
//		}
//
//		boolean hasTop() {
//			return !push.isEmpty();
//		}
//
//		Class<?> top() {
//			return push.getLast();
//		}
//
//		Operation combine(
//			Operation other
//		) {
//			return this.combine(other.pop(), other.push(), other.raws(), other.instructions());
//		}
//
//		Operation combine(
//			final int pop,
//			final Class<?> push,
//			final Optional<?> raws,
//			final MethodNode insns
//		) {
//			return this.combine(pop, List.of(push), List.of(raws), insns.instructions);
//		}
//
//		Operation combine(
//			final int pop,
//			final List<Class<?>> push,
//			final List<Optional<?>> raws,
//			final MethodNode insns
//		) {
//			return this.combine(pop, push, raws, insns.instructions);
//		}
//
//		Operation combine(
//			int pop,
//			final List<Class<?>> push,
//			final List<Optional<?>> raws,
//			final InsnList insns
//		) {
//			final int postPopped = this.push().size() - pop;
//
//			if (postPopped < 0) {
//				throw new IllegalArgumentException("overpopped: " + this + "#mux(" + pop + ", " + push + ", " + raws + ", " + insns + ")");
//			}
//
//			final var apush = new Class<?>[push.size() + postPopped];
//			copy(this.push(), 0, apush, 0, postPopped);
//			copy(push, 0, apush, postPopped, push.size());
//
//			final var araws = new Optional<?>[push.size() + postPopped];
//			copy(this.raws(), 0, araws, 0, postPopped);
//			copy(raws, 0, araws, postPopped, raws.size());
//
//			final var ainsn = new MethodNode();
//
//			this.instructions().accept(ainsn);
//			insns.accept(ainsn);
//
//			return new Operation(
//				0,
//				List.of(apush),
//				List.of(araws),
//				ainsn.instructions
//			);
//		}
//
//		private static <T> void copy(
//			final List<T> src,
//			final int sst,
//			final T[] dest,
//			final int dst,
//			final int len
//		) {
//			for (int i = 0; i < len; i++) {
//				dest[dst + i] = src.get(sst + i);
//			}
//		}
//	}
}
