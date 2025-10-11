package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.BreakExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.ContinueExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.IdentifierExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.ReturnExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.binding.Alias;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Ampflower
 **/
public sealed abstract class Compiler<T> permits MolangCompiler {
	private static final Logger logger = Util.logger();

	private final MethodHandles.Lookup lookup;
	protected final Linker linker;
	protected final Class<T> type;

	protected Compiler(
		final MethodHandles.Lookup lookup,
		final Class<T> type,
		final Linker linker
	) {
		this.lookup = lookup;
		this.type = type;
		this.linker = linker;
	}

	public abstract T compile(String program);

	protected final byte[] compileToBytes(
		final String program,
		final Expression expression
	) {
		final var method = this.linker.tryFunctionalClass(this.type)
			.orElseThrow(() -> new IllegalArgumentException("clazz " + this.type + " has no suitable methods"));
		final var writer = Jit.generateStub(this.lookup, this.type, method, program);

		writer.setFlags(ClassWriter.COMPUTE_MAXS);

		this.compile(writer, method, expression);

		writer.visitEnd();

		return writer.toByteArray();
	}

	protected final CompilerResult<T> compile(
		final String program,
		final Expression expression
	) {
		final var bytes = compileToBytes(program, expression);

		Decompiler decompiler = Decompiler.builder()
			.inputs(new ByteArrayContextSource("", bytes))
			.output(new ConsoleFileSaver(null))
			.build();

		decompiler.decompile();

		try {
			final var clazz = Class.forName("com.sun.tools.javap.Main");

			final var method = clazz.getMethod("run", String[].class, PrintWriter.class);

			method.setAccessible(true);

			final var temp = Files.createTempFile("meow", ".class");

			Files.write(temp, bytes);

			method.invoke(
				null,
				new String[]{"-v", "-p", "-c", temp.normalize().toString()},
				new PrintWriter(System.err) {
					@Override
					public void close() {
						// no-op
					}
				}
			);

			Files.delete(temp);

		} catch (Exception roe) {
			// Frankly, it doesn't matter; it's only for debugging anyway.
			roe.printStackTrace();
		}

		try {
			final var result = this.lookup.defineHiddenClassWithClassData(bytes, this, true);

			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	protected final void compile(
		final ClassWriter writer,
		final Method method,
		final Expression expression
	) {
		writer.setFlags(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		final var visitor = writer.visitMethod(
			Jit.ACC_PUBLISH,
			method.getName(),
			Type.getMethodDescriptor(method),
			null,
			null
		);

		final var returnType = method.getReturnType();
		final Operation operation;

		try {
			operation = compile(this.linker, new Context(method), expression);
		} catch (Throwable t) {
			throw new IllegalArgumentException(expression.toStr(), t);
		}

		visitor.visitCode();

		operation.insns().accept(visitor);

		if (returnType == void.class) {
			visitor.visitInsn(Opcodes.RETURN);
		} else if (operation.hasTop()) {
			try {
				Jit.visitReturn(visitor, operation.top(), returnType);
			} catch (Exception e) {
				throw new IllegalArgumentException(
					"non-returnable: " + operation.top() + "; " + operation + "\n" + expression,
					e
				);
			}
		}

		visitor.visitMaxs(0, 0);
		visitor.visitEnd();
	}

	private static Operation compile(
		final Linker linker,
		final Context context,
		final Expression primitive
	) throws Throwable {
		try {
			return switch (primitive) {
				case AccessExpression exp -> {
					final var insns = new MethodNode();
					final Field field;
					final Operation op;
					final Protoparameter param;
					if (exp.object() instanceof IdentifierExpression iexp
						&& (param = context.localTable().get(iexp.value())) != null
					) {
						final var get = new MethodNode();
						Jit.visitLoad(get, param.type, param.index);
						op = new Operation(0, param.type, Optional.empty(), get);
						field = linker.findField(param.type, exp.toAccess());
					} else {
						logger.debug("{} => {}", exp, context);
						op = compile(linker, context, exp.object());
						field = linker.findField(op.top(), exp.toAccess());
					}
					Jit.visitFieldGet(insns, field);
					if (Modifier.isStatic(field.getModifiers())) {
						yield new Operation(0, (Class) field.getType(), Optional.ofNullable(field.get(null)), insns);
					} else {
						yield op.mux(1, field.getType(), Optional.empty(), insns);
					}
				}
				case ArrayAccessExpression exp -> {
					final var array = compile(linker, context, exp.array());
					final var field = compile(linker, context, exp.toAccess());

					throw new UnsupportedOperationException();
				}
				case BinaryOperationExpression exp -> {
					final var left = compile(linker, context, exp.left());
					final var right = compile(linker, context, exp.right());

					if (left.push().size() != 1 || left.pop() != 0) {
						throw new IllegalStateException("left (" + left + ") must be at stack 1: " + exp.left());
					}

					if (right.push().size() != 1 || right.pop() != 0) {
						throw new IllegalStateException("right (" + right + ") must be at stack 1: " + exp.right());
					}

					final var insns = new MethodNode();
					Class<?> clazz = null;

					switch (exp.operator()) {
						case SET -> {
							throw new UnsupportedOperationException();
						}
						case NULL_COALESCE -> {
							throw new UnsupportedOperationException();
						}
						case CONDITIONAL -> {
							throw new UnsupportedOperationException();
						}
						case LOGICAL_OR -> {
							throw new UnsupportedOperationException();
						}
						case LOGICAL_AND -> {
							throw new UnsupportedOperationException();
						}
						case EQUAL_TO -> {
							throw new UnsupportedOperationException();
						}
						case NOT_EQUAL -> {
							throw new UnsupportedOperationException();
						}
						case LESS_THAN -> {
							throw new UnsupportedOperationException();
						}
						case GREATER_THAN -> {
							throw new UnsupportedOperationException();
						}
						case LESS_THAN_OR_EQUAL_TO -> {
							throw new UnsupportedOperationException();
						}
						case GREATER_THAN_OR_EQUAL_TO -> {
							throw new UnsupportedOperationException();
						}
						case ADD -> {
							// FIXME: boxing, constant inlining
							left.insns.accept(insns);
							Jit.visitCoerce(insns, left.top(), float.class);
							right.insns.accept(insns);
							Jit.visitCoerce(insns, right.top(), float.class);
							insns.visitInsn(Opcodes.FADD);
							clazz = float.class;
						}
						case SUBTRACT -> {
							// FIXME: boxing, constant inlining
							left.insns.accept(insns);
							Jit.visitCoerce(insns, left.top(), float.class);
							right.insns.accept(insns);
							Jit.visitCoerce(insns, right.top(), float.class);
							insns.visitInsn(Opcodes.FSUB);
							clazz = float.class;
						}
						case MULTIPLY -> {
							// FIXME: boxing, constant inlining
							left.insns.accept(insns);
							Jit.visitCoerce(insns, left.top(), float.class);
							right.insns.accept(insns);
							Jit.visitCoerce(insns, right.top(), float.class);
							insns.visitInsn(Opcodes.FMUL);
							clazz = float.class;
						}
						case DIVIDE -> {
							// FIXME: boxing, constant inlining
							left.insns.accept(insns);
							Jit.visitCoerce(insns, left.top(), float.class);
							right.insns.accept(insns);
							Jit.visitCoerce(insns, right.top(), float.class);
							insns.visitInsn(Opcodes.FDIV);
							clazz = float.class;
						}
						case ARROW -> {
							throw new UnsupportedOperationException();
						}
					}

					yield new Operation(0, clazz, Optional.empty(), insns);
				}
				case BreakExpression exp -> {
					throw new UnsupportedOperationException();
				}
				case ComplexExpression exp -> {
					Operation op = new Operation();
					for (final var e : exp.expressions()) {
						op = op.mux(compile(linker, context, e));
					}
					yield op;
				}
				case ContinueExpression exp -> {
					throw new UnsupportedOperationException();
				}
				case FunctionCallExpression exp -> {
					if (!(exp.function() instanceof AccessExpression acc)) {
						throw new UnsupportedOperationException("not an access: " + exp.function());
					}
					final var funcBase = compile(linker, context, acc.object());
					final var args = new Operation[exp.arguments().size()];
					final var cargs = new Class<?>[args.length];

					for (int i = 0; i < args.length; i++) {
						final var aexp = exp.arguments().get(i);
						final var arg = compile(linker, context, aexp);
						if (arg.pop != 0) {
							throw new IllegalArgumentException("argument " + aexp + " pops: " + arg);
						}
						if (arg.push.size() != 1) {
							throw new IllegalArgumentException("argument " + aexp + " fails to push single entry: " + arg);
						}
						args[i] = arg;
						cargs[i] = arg.top();
					}

					final var insns = new MethodNode();

					final var method = linker.findMethod(funcBase.top(), acc.toAccess(), cargs);
					final var modifiers = method.getModifiers();
					final boolean isInterface;
					final int invokeOpcode;

					if (Modifier.isStatic(modifiers)) {
						isInterface = false;
						invokeOpcode = Opcodes.INVOKESTATIC;
					} else {
						funcBase.insns.accept(insns);
						isInterface = method.getDeclaringClass().isInterface();
						invokeOpcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
					}

					final var params = method.getParameterTypes();

					for (int i = 0; i < params.length; i++) {
						args[i].insns.accept(insns);
						Jit.visitCoerce(insns, cargs[i], params[i]);
					}

					insns.visitMethodInsn(
						invokeOpcode,
						Type.getInternalName(method.getDeclaringClass()),
						method.getName(),
						Type.getMethodDescriptor(method),
						isInterface
					);

					yield new Operation(0, (Class) method.getReturnType(), Optional.empty(), insns);
				}
				case IdentifierExpression exp -> {
					final Pair<Class<?>, ?> pair = switch (exp.value().toLowerCase()) {
						case "math" -> Pair.of(Math.class, Math.class);
						default -> throw new UnsupportedOperationException("field: " + exp.value());
					};
					yield new Operation(0, (Class) pair.key(), Optional.ofNullable(pair.value()), new InsnList());
				}
				case NumberExpression exp -> {
					final var insns = new MethodNode();
					if (exp == NumberExpression.ZERO) {
						insns.visitInsn(Opcodes.FCONST_0);
					} else if (exp == NumberExpression.ONE) {
						insns.visitInsn(Opcodes.FCONST_1);
					} else {
						Jit.visitFloat(insns, exp.value());
					}
					yield new Operation(0, float.class, Optional.of(exp.value()), insns);
				}
				case ReturnExpression exp -> {
					final var value = compile(linker, context, exp.value());

					final var signature = context.signature();
					if (!signature.returnType().isAssignableFrom(value.top())) {
						throw new IllegalArgumentException(value + " != " + signature.returnType());
					}

					final var insns = new InsnList();
					insns.insert(new InsnNode(Primitives.returnOpcodeOf(signature.returnType())));
					yield value.mux(1, List.of(), List.of(), insns);
				}
				case StringExpression exp -> {
					final var insns = new InsnList();
					insns.add(new LdcInsnNode(exp.value()));
					yield new Operation(0, String.class, Optional.of(exp.value()), insns);
				}
				case TernaryOperationExpression exp -> {
					throw new UnsupportedOperationException();
				}
				case UnaryOperationExpression exp -> {
					throw new UnsupportedOperationException();
				}
			};
		} catch (UnsupportedOperationException e) {
			throw new UnsupportedOperationException("unsupported: " + primitive.toStr(), e);
		} catch (Throwable t) {
			throw new IllegalArgumentException("expr: " + primitive.toStr(), t);
		}
	}

	private record Context(
		MethodType signature,
		Map<String, Protoparameter> localTable
	) {
		Context(
			final Method method
		) {
			this(
				MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
				toLocalTable(method)
			);
		}

		// TODO: find a better spot for this?
		private static Map<String, Protoparameter> toLocalTable(final Method method) {
			final var map = new HashMap<String, Protoparameter>();
			final var params = method.getParameters();
			int offset = Modifier.isStatic(method.getModifiers()) ? 0 : 1;

			for (int i = 0; i < params.length; i++) {
				final var proto = new Protoparameter(i + offset, params[i].getType());
				if (proto.type == long.class || proto.type == double.class) {
					// 2 slots
					offset++;
				}
				final var alias = params[i].getAnnotation(Alias.class);
				if (alias == null) {
					continue;
				}
				for (final var str : alias.value()) {
					map.put(str, proto);
				}
			}

			return Map.copyOf(map);
		}
	}

	private record Protoparameter(
		int index,
		Class<?> type
	) {
	}

	private record Operation(
		int pop,
		List<Class<?>> push,
		List<Optional<?>> raws,
		InsnList insns
	) {
		<T> Operation(
			final int pop,
			final Class<T> push,
			final Optional<T> raws,
			final MethodNode insns
		) {
			this(pop, List.of(push), List.of(raws), insns.instructions);
		}

		<T> Operation(
			final int pop,
			final Class<T> push,
			final Optional<T> raws,
			final InsnList insns
		) {
			this(pop, List.of(push), List.of(raws), insns);
		}

		Operation() {
			this(0, List.of(), List.of(), new InsnList());
		}

		boolean hasTop() {
			return !push.isEmpty();
		}

		Class<?> top() {
			return push.getLast();
		}

		Operation mux(
			Operation other
		) {
			return this.mux(other.pop(), other.push(), other.raws(), other.insns());
		}

		Operation mux(
			final int pop,
			final Class<?> push,
			final Optional<?> raws,
			final MethodNode insns
		) {
			return this.mux(pop, List.of(push), List.of(raws), insns.instructions);
		}

		Operation mux(
			final int pop,
			final List<Class<?>> push,
			final List<Optional<?>> raws,
			final MethodNode insns
		) {
			return this.mux(pop, push, raws, insns.instructions);
		}

		Operation mux(
			int pop,
			final List<Class<?>> push,
			final List<Optional<?>> raws,
			final InsnList insns
		) {
			final int postPopped = this.push().size() - pop;

			if (postPopped < 0) {
				throw new IllegalArgumentException("overpopped: " + this + "#mux(" + pop + ", " + push + ", " + raws + ", " + insns + ")");
			}

			final var apush = new Class<?>[push.size() + postPopped];
			copy(this.push(), 0, apush, 0, postPopped);
			copy(push, 0, apush, postPopped, push.size());

			final var araws = new Optional<?>[push.size() + postPopped];
			copy(this.raws(), 0, araws, 0, postPopped);
			copy(raws, 0, araws, postPopped, raws.size());

			final var ainsn = new MethodNode();

			this.insns().accept(ainsn);
			insns.accept(ainsn);

			return new Operation(
				0,
				List.of(apush),
				List.of(araws),
				ainsn.instructions
			);
		}

		private static <T> void copy(
			final List<T> src,
			final int sst,
			final T[] dest,
			final int dst,
			final int len
		) {
			for (int i = 0; i < len; i++) {
				dest[dst + i] = src.get(sst + i);
			}
		}
	}
}
