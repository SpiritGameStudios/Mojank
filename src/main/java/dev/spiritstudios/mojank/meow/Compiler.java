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
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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

	public abstract CompilerResult<T> compile(String program);

	protected final CompilerResult<T> compile(
		final String program,
		final Expression expression
	) {
		final var writer = Jit.generateStub(this.lookup, this.linker, this.type, program);

		writer.setFlags(ClassWriter.COMPUTE_MAXS);

		writer.visitEnd();

		final var bytes = writer.toByteArray();


		Decompiler decompiler = Decompiler.builder()
			.inputs(new ByteArrayContextSource("", bytes))
			.output(new ConsoleFileSaver(null))
			.build();

		decompiler.decompile();

		try {
			final var result = this.lookup.defineHiddenClassWithClassData(bytes, this, true);

			return (CompilerResult<T>) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class))
				.invoke();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	protected final void compile(
		final MethodVisitor visitor,
		final Expression expression
	) {
	}

	private static Operation compile(
		final Linker linker,
		final MethodVisitor visitor,
		final MethodType signature,
		final Context context,
		final Expression primitive
	) throws Throwable {
		return switch (primitive) {
			case AccessExpression exp -> {
				final var op = compile(linker, visitor, signature, context, exp.object());
				final var insns = new MethodNode();
				final var field = linker.findField(op.top(), exp.toAccess());
				Jit.visitFieldGet(insns, field);
				if (Modifier.isStatic(field.getModifiers())) {
					yield new Operation(0, (Class) field.getType(), field.get(null), insns);
				} else {
					yield op.mux(1, field.getType(), null, insns);
				}
			}
			case ArrayAccessExpression exp -> {
				final var array = compile(linker, visitor, signature, context, exp.array());
				final var field = compile(linker, visitor, signature, context, exp.toAccess());

				throw new UnsupportedOperationException();
			}
			case BinaryOperationExpression exp -> {
				final var left = compile(linker, visitor, signature, context, exp.left());
				final var right = compile(linker, visitor, signature, context, exp.right());
				final var insns = new MethodNode();

				throw new UnsupportedOperationException();
			}
			case BreakExpression exp -> {
				throw new UnsupportedOperationException();
			}
			case ComplexExpression exp -> {
				Operation op = new Operation();
				for (final var e : exp.expressions()) {
					op = op.mux(compile(linker, visitor, signature, context, e));
				}
				yield op;
			}
			case ContinueExpression exp -> {
				throw new UnsupportedOperationException();
			}
			case FunctionCallExpression exp -> {
				throw new UnsupportedOperationException();
			}
			case IdentifierExpression exp -> {
				throw new UnsupportedOperationException();
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
				yield new Operation(0, float.class, exp.value(), insns);
			}
			case ReturnExpression exp -> {
				/*
				if (!signature.returnType().isAssignableFrom(stack.getLast())) {
					throw new IllegalStateException(signature + " does not support the following stack: " + stack + "\n" + exp);
				}
				context.stack.removeLast();
				*/
				final var insns = new InsnList();
				insns.insert(new InsnNode(Jit.opcodeOfReturn(signature.returnType())));
				yield new Operation(1, List.of(), List.of(), insns);
			}
			case StringExpression exp -> {
				final var insns = new InsnList();
				insns.add(new LdcInsnNode(exp));
				yield new Operation(0, List.of(String.class), List.of(exp.value()), insns);
			}
			case TernaryOperationExpression exp -> {
				throw new UnsupportedOperationException();
			}
			case UnaryOperationExpression exp -> {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static class Context {
		final List<Class<?>> stack = new ArrayList<>();
		int count, stackMax;

		void push(Class<?> clazz) {
			if (clazz == long.class || clazz == double.class) {
				count += 2;
			} else {
				count += 1;
			}
			stackMax = Math.max(count, stackMax);
		}
	}

	private record Operation(
		int pop,
		List<Class<?>> push,
		List<Object> raws,
		InsnList insns
	) {
		<T> Operation(
			int pop,
			Class<T> push,
			T raws,
			MethodNode insns
		) {
			this(pop, List.of(push), List.of(raws), insns.instructions);
		}

		Operation() {
			this(0, List.of(), List.of(), new InsnList());
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
			int pop,
			Class<?> push,
			Object raws,
			MethodNode insns
		) {
			return this.mux(pop, List.of(push), List.of(raws), insns.instructions);
		}

		Operation mux(
			int pop,
			List<Class<?>> push,
			List<Object> raws,
			MethodNode insns
		) {
			return this.mux(pop, push, raws, insns.instructions);
		}

		Operation mux(
			int pop,
			List<Class<?>> push,
			List<Object> raws,
			InsnList insns
		) {
			final int postPopped = this.push().size() - pop;

			final var apush = new Class<?>[push.size() + postPopped];
			copy(this.push(), 0, apush, 0, postPopped);
			copy(push, 0, apush, postPopped, push.size());

			final var araws = new Object[push.size() + postPopped];
			copy(this.raws(), 0, araws, 0, postPopped);
			copy(raws, 0, araws, postPopped, raws.size());

			if (pop > this.push().size()) {
				pop = Math.max(this.pop, pop - this.push().size());
			}

			final var ainsn = new MethodNode();

			this.insns().accept(ainsn);
			insns.accept(ainsn);

			return new Operation(
				pop,
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
