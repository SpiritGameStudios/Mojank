package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.internal.Util;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_1;
import static org.objectweb.asm.Opcodes.FCONST_2;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.V17;

/**
 * @author Ampflower
 **/
@ApiStatus.Internal
final class Jit {
	private static final Logger logger = Util.logger();

	private static final Map<Class<?>, Class<?>> primitiveLookup = Map.of(
		void.class, Void.class,
		byte.class, Byte.class,
		short.class, Short.class,
		char.class, Character.class,
		int.class, Integer.class,
		long.class, Long.class,
		float.class, Float.class,
		double.class, Double.class
	);

	private static final Map<Class<?>, Integer> primitiveOpcode = Map.of(
		void.class, RETURN,
		byte.class, IRETURN,
		short.class, IRETURN,
		char.class, IRETURN,
		int.class, IRETURN,
		long.class, LRETURN,
		float.class, FRETURN,
		double.class, DRETURN
	);

	private static final int ACC_CONST = ACC_STATIC | ACC_FINAL;
	private static final int ACC_PUBLISH = ACC_FINAL | ACC_PUBLIC;
	private static final int ACC_INTERNAL = ACC_FINAL | ACC_PRIVATE | ACC_STATIC;

	private static final Map<Integer, Integer> FLOATS = Map.of(
		Float.floatToRawIntBits(0F), FCONST_0,
		Float.floatToRawIntBits(1F), FCONST_1,
		Float.floatToRawIntBits(2F), FCONST_2 // but, why
	);

	private static final Map<Long, Integer> DOUBLES = Map.of(
		Double.doubleToRawLongBits(0D), DCONST_0,
		Double.doubleToRawLongBits(1D), DCONST_1
	);

	// These would be lighter in bytecode to directly inline.
	private static final Set<Class<?>> INLINE = Set.of(
		String.class,
		Class.class,
		byte.class,
		short.class,
		char.class,
		int.class,
		long.class,
		float.class,
		double.class
	);

	private static final String SELF = "⚧️️";
	private static final String HANDLE = "☃";

	private static final String genericCondy = descriptor(
		Object.class,
		MethodHandles.Lookup.class,
		String.class,
		Class.class
	);

	private static final String genericCondyWithClass = descriptor(
		Object.class,
		MethodHandles.Lookup.class,
		String.class,
		Class.class,
		Class.class
	);

	// condy partly derived from https://jornvernee.github.io/jli/methodhandles/asm/2022/05/01/class-data.html
	private static final Handle classDescHandle = new Handle(
		H_INVOKESTATIC,
		Type.getInternalName(MethodHandles.class),
		"classData",
		genericCondy,
		false
	);

	private static final ConstantDynamic compilerDynamic = new ConstantDynamic(
		ConstantDescs.DEFAULT_NAME,
		Compiler.class.descriptorString(),
		classDescHandle
	);

	private static final Handle condyStaticFinal = new Handle(
		H_INVOKESTATIC,
		Type.getInternalName(ConstantBootstraps.class),
		"getStaticFinal",
		genericCondy,
		false
	);

	private static final Handle condyStaticFinalWithDeclaringClass = new Handle(
		H_INVOKESTATIC,
		Type.getInternalName(ConstantBootstraps.class),
		"getStaticFinal",
		genericCondyWithClass,
		false
	);

	static ClassWriter generateStub(
		final @NotNull MethodHandles.Lookup lookup,
		final @NotNull Linker linker,
		final @NotNull Class<?> clazz,
		final @NotNull String source
	) {
		Method target = null;

		for (final var method : clazz.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())) {
				logger.debug("Static: {}", method);
				continue;
			}

			if (!Modifier.isAbstract(method.getModifiers())) {
				logger.debug("Not abstract: {}", method);
				continue;
			}

			if (!linker.isPermitted(method.getParameterTypes())) {
				logger.debug("Denied: {}", method);
				continue;
			}

			target = method;
			break;
		}

		if (target == null) {
			throw new IllegalArgumentException("clazz " + clazz + " has no suitable methods");
		}

		return generateStub(lookup, clazz, target, source);
	}

	static ClassWriter generateStub(
		final @NotNull MethodHandles.Lookup lookup,
		final @NotNull Class<?> clazz,
		final @NotNull Method target,
		final @NotNull String source
	) {
		final var writer = new ClassWriter(0);

		final var pack = lookup.lookupClass().getPackage().getName().replace('.', '/');

		writeStub(
			writer,
			Type.getObjectType(pack + "/\uD83C\uDFF3️\u200D⚧️️" + clazz.getSimpleName()),
			clazz,
			target,
			source
		);

		return writer;
	}

	static void writeStub(
		final ClassWriter writer,
		final Type self,
		final Class<?> soup,
		final Method target,
		final String source
	) {
		writer.visit(
			V17,
			ACC_INTERNAL, //ACC_FINAL | ACC_STATIC | ACC_PROTECTED,
			self.getInternalName(),
			null,
			Type.getInternalName(!soup.isInterface() ? soup : Object.class),
			soup.isInterface() ? new String[]{
				Type.getInternalName(soup),
				Type.getInternalName(CompilerResult.class)
			} : new String[]{
				Type.getInternalName(CompilerResult.class)
			}
		);
		writer.setFlags(0);

		// writer.visitField(ACC_SINT, SELF, soup.descriptorString(), null, null);
		// writer.visitField(ACC_SINT, HANDLE, MethodHandle.class.descriptorString(), null, null);

		// visitBootBlock(writer, self, soup, target);
		visitConstructBlock(writer, self, soup);
		visitEqualsBlock(writer, source);
		visitHashCodeBlock(writer, source);
		visitGetTypeBlock(writer, Type.getType(target.getDeclaringClass()));
		visitGetCompilerBlock(writer);
		visitGetHandleBlock(writer, self, target);
		visitToStringBlock(writer, source);
	}

	static void visitEqualsBlock(
		final ClassWriter writer,
		final String source
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "equals", "(Ljava/lang/Object;)Z", null, null);

		visitLocal(visitor, ALOAD, 0);
		visitLocal(visitor, ALOAD, 1);

		{
			final Label skip = new Label();
			visitor.visitJumpInsn(IF_ACMPNE, skip);

			visitor.visitInsn(ICONST_1);
			visitor.visitInsn(IRETURN);

			visitor.visitLabel(skip);
			visitor.visitFrame(F_SAME, 0, null, 0, null);
		}

		visitLocal(visitor, ALOAD, 1);
		visitor.visitTypeInsn(INSTANCEOF, Type.getInternalName(CompilerResult.class));

		final Label skip = new Label();
		visitor.visitJumpInsn(IFEQ, skip);

		visitor.visitLdcInsn(source);

		visitLocal(visitor, ALOAD, 1);
		visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);

		visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
		visitor.visitInsn(IRETURN);

		visitor.visitLabel(skip);
		visitor.visitFrame(F_SAME, 0, null, 0, null);

		visitor.visitInsn(ICONST_0);
		visitor.visitInsn(IRETURN);

		visitor.visitMaxs(2, 2);
		visitor.visitEnd();
	}

	static void visitHashCodeBlock(
		final ClassWriter writer,
		final String source
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "hashCode", "()I", null, null);

		visitInt(visitor, source.hashCode());
		visitor.visitInsn(IRETURN);

		visitor.visitMaxs(1, 1);
		visitor.visitEnd();
	}

	static void visitGetTypeBlock(
		final ClassWriter writer,
		final Type source
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "getType", "()Ljava/lang/Class;", null, null);

		visitor.visitLdcInsn(source);
		visitor.visitInsn(ARETURN);

		visitor.visitMaxs(1, 1);
		visitor.visitEnd();
	}

	static void visitGetCompilerBlock(
		final ClassWriter writer
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "getCompiler", descriptor(Compiler.class), null, null);

		visitor.visitLdcInsn(compilerDynamic);
		visitor.visitInsn(ARETURN);

		visitor.visitMaxs(1, 1);
		visitor.visitEnd();
	}

	static void visitGetHandleBlock(
		final ClassWriter writer,
		final Type self,
		final Method target
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "toHandle", descriptor(MethodHandle.class), null, null);

		visitor.visitLdcInsn(
			new Handle(
				H_INVOKEVIRTUAL,
				self.getInternalName(),
				target.getName(),
				descriptor(target.getReturnType(), target.getParameterTypes()),
				false
			)
		);
		visitLocal(visitor, ALOAD, 0);

		visitor.visitMethodInsn(
			INVOKEVIRTUAL,
			Type.getInternalName(MethodHandle.class),
			"bindTo",
			descriptor(MethodHandle.class, Object.class),
			false
		);

		// visitor.visitFieldInsn(GETSTATIC, self.getInternalName(), HANDLE, MethodHandle.class.descriptorString());
		visitor.visitInsn(ARETURN);

		visitor.visitMaxs(2, 1);
		visitor.visitEnd();
	}

	static void visitToStringBlock(
		final ClassWriter writer,
		final String source
	) {
		final var visitor = writer.visitMethod(ACC_PUBLISH, "toString", "()Ljava/lang/String;", null, null);

		visitor.visitLdcInsn(source);
		visitor.visitInsn(ARETURN);

		visitor.visitMaxs(1, 1);
		visitor.visitEnd();
	}

	static void visitBootBlock(
		final ClassWriter writer,
		final Type self,
		final Class<?> soup,
		final Method target
	) {
		final var visitor = writer.visitMethod(
			ACC_INTERNAL,
			"<clinit>",
			"()V",
			null,
			null
		);

		visitor.visitLdcInsn(new Handle(
			H_INVOKEVIRTUAL,
			self.getInternalName(),
			target.getName(),
			descriptor(target.getReturnType(), target.getParameterTypes()),
			false
		));

		visitor.visitTypeInsn(NEW, self.getInternalName());
		visitor.visitInsn(DUP);
		visitor.visitMethodInsn(INVOKESPECIAL, self.getInternalName(), "<init>", "()V", false);
		visitor.visitInsn(DUP);
		visitor.visitFieldInsn(PUTSTATIC, self.getInternalName(), SELF, soup.descriptorString());

		visitor.visitMethodInsn(
			INVOKEVIRTUAL,
			Type.getInternalName(MethodHandle.class),
			"bindTo",
			descriptor(MethodHandle.class, Object.class),
			false
		);

		visitor.visitFieldInsn(PUTSTATIC, self.getInternalName(), HANDLE, MethodHandle.class.descriptorString());
		visitor.visitInsn(RETURN);

		visitor.visitMaxs(3, 0);
		visitor.visitEnd();
	}

	static void visitConstructBlock(
		final ClassWriter writer,
		final Type self,
		final Class<?> soup
	) {
		final var visitor = writer.visitMethod(
			ACC_PRIVATE,
			"<init>",
			descriptor(void.class),
			null,
			null
		);

		final String sup;
		if (soup.isInterface()) {
			sup = "java/lang/Object";
		} else {
			sup = Type.getInternalName(soup);
		}

		visitLocal(visitor, ALOAD, 0);
		visitor.visitMethodInsn(INVOKESPECIAL, sup, "<init>", "()V", false);

		visitor.visitInsn(RETURN);

		visitor.visitMaxs(2, 2);
		visitor.visitEnd();
	}

	static void visitMethodType(
		final MethodVisitor writer,
		final Class<?> ret,
		final Class<?>... args
	) {
		visitClass(writer, ret);
		visitClassArray(writer, args);
		writer.visitMethodInsn(
			INVOKESTATIC,
			Type.getInternalName(MethodType.class),
			"methodType",
			descriptor(MethodType.class, Class.class, Class[].class),
			false
		);
	}

	@SafeVarargs
	static <T> void visitArray(
		final MethodVisitor writer,
		final Type type,
		final Function<T, Type> trans,
		final T... array
	) {
		visitInt(writer, array.length);
		writer.visitTypeInsn(ANEWARRAY, type.getInternalName());

		for (int i = 0; i < array.length; i++) {
			writer.visitInsn(DUP);
			visitInt(writer, i);
			writer.visitLdcInsn(trans.apply(array[i]));
			writer.visitInsn(AASTORE);
		}
	}

	static void visitClassArray(
		final MethodVisitor writer,
		final Class<?>... array
	) {
		visitInt(writer, array.length);
		writer.visitTypeInsn(ANEWARRAY, Type.getInternalName(Class.class));

		for (int i = 0; i < array.length; i++) {
			writer.visitInsn(DUP);
			visitInt(writer, i);
			visitClass(writer, array[i]);
			writer.visitInsn(AASTORE);
		}
	}

	static void visitClass(
		final MethodVisitor writer,
		final Class<?> value
	) {
		if (value.isPrimitive()) {
			writer.visitFieldInsn(
				GETSTATIC,
				Type.getInternalName(primitiveLookup.get(value)),
				"TYPE",
				Class.class.descriptorString()
			);
		} else {
			writer.visitLdcInsn(Type.getType(value));
		}
	}

	static String descriptor(Class<?> ret, Class<?>... args) {
		final var bui = new StringBuilder();
		bui.append('(');
		for (final var cla : args) {
			bui.append(cla.descriptorString());
		}
		bui.append(')');
		bui.append(ret.descriptorString());
		return bui.toString();
	}

	// For some reason, ASM isn't doing this automatically.
	// So, instead, do it manually. :3
	// Note: manual computation is required when using this.
	private static void visitLocal(
		final MethodVisitor visitor,
		final int opcode,
		final int index
	) {
		if (index < 0) {
			throw new AssertionError(index);
		}
		if (index > 3) {
			visitor.visitIntInsn(opcode, index);
			return;
		}
		final int insn = switch (opcode) {
			case ILOAD -> 26;
			case LLOAD -> 30;
			case FLOAD -> 34;
			case DLOAD -> 38;
			case ALOAD -> 42;
			case ISTORE -> 59;
			case LSTORE -> 63;
			case FSTORE -> 67;
			case DSTORE -> 71;
			case ASTORE -> 75;
			default -> throw new AssertionError(opcode);
		};
		visitor.visitInsn(insn + index);
	}

	static void visitInt(
		final MethodVisitor writer,
		final int value
	) {
		switch (value) {
			case -1 -> writer.visitInsn(ICONST_M1);
			case 0 -> writer.visitInsn(ICONST_0);
			case 1 -> writer.visitInsn(ICONST_1);
			case 2 -> writer.visitInsn(ICONST_2);
			case 3 -> writer.visitInsn(ICONST_3);
			case 4 -> writer.visitInsn(ICONST_4);
			case 5 -> writer.visitInsn(ICONST_5);
			default -> {
				if ((value & 255) == value) {
					writer.visitIntInsn(BIPUSH, value);
				} else if ((value & 65535) == value) {
					writer.visitIntInsn(SIPUSH, value);
				} else {
					writer.visitLdcInsn(value);
				}
			}
		}
	}

	static void visitLong(
		final MethodVisitor visitor,
		final long value
	) {
		if (value == 0L) {
			visitor.visitInsn(LCONST_0);
		} else if (value == 1L) {
			visitor.visitInsn(LCONST_1);
		} else if ((int) value == value) {
			visitInt(visitor, (int) value);
			visitor.visitInsn(I2L);
		} else {
			visitor.visitLdcInsn(value);
		}
	}

	static void visitFloat(
		final MethodVisitor visitor,
		final float value
	) {
		final var opcode = FLOATS.get(Float.floatToRawIntBits(value));
		if (opcode != null) {
			visitor.visitInsn(opcode);
		} else {
			visitor.visitLdcInsn(value);
		}
	}

	static void visitDouble(
		final MethodVisitor visitor,
		final double value
	) {
		final var opcode = DOUBLES.get(Double.doubleToRawLongBits(value));
		if (opcode != null) {
			visitor.visitInsn(opcode);
		} else {
			visitor.visitLdcInsn(value);
		}
	}

	static int opcodeOfReturn(
		final Class<?> returnType
	) {
		return primitiveOpcode.getOrDefault(returnType, ARETURN);
	}

	static void visitFieldGet(final MethodVisitor visitor, final Field field) {
		if ((field.getModifiers() & ACC_CONST) == ACC_CONST && visitConstant(visitor, field)) {
			return;
		}
		visitor.visitFieldInsn(
			Modifier.isStatic(field.getModifiers()) ? GETSTATIC : GETFIELD,
			Type.getInternalName(field.getDeclaringClass()),
			field.getName(),
			field.getType().descriptorString()
		);
	}

	static boolean visitConstant(
		final MethodVisitor visitor,
		final Field field
	) {
		try {
			final var object = field.get(null);
			// TODO: determine boxing rules
			if (object != null && !INLINE.contains(field.getType())) {
				return false;
			}
			if (!visitConstantValue(visitor, object)) {
				visitConstantField(visitor, field);
			}
		} catch (ReflectiveOperationException roe) {
			throw new AssertionError(field.toString(), roe);
		}
		return true;
	}

	static void visitConstantField(
		final MethodVisitor visitor,
		final Field field
	) {
		final ConstantDynamic dynamic;
		if (field.getType() == field.getDeclaringClass()) {
			dynamic = new ConstantDynamic(
				field.getName(),
				field.getType().descriptorString(),
				condyStaticFinal
			);
		} else {
			dynamic = new ConstantDynamic(
				field.getName(),
				field.getType().descriptorString(),
				condyStaticFinalWithDeclaringClass,
				field.getDeclaringClass()
			);
		}
		visitor.visitLdcInsn(dynamic);
	}

	static boolean visitConstantValue(
		final MethodVisitor visitor,
		final Object object
	) {
		switch (object) {
			case Handle value -> visitor.visitLdcInsn(value);
			case Type value -> visitor.visitLdcInsn(value);

			// Primitives
			case Byte value -> visitInt(visitor, value);
			case Short value -> visitInt(visitor, value);
			case Character value -> visitInt(visitor, value);
			case Integer value -> visitInt(visitor, value);
			case Long value -> visitLong(visitor, value);
			case Float value -> visitFloat(visitor, value);
			case Double value -> visitDouble(visitor, value);
			case String value -> visitor.visitLdcInsn(value);
			case Class<?> value -> visitClass(visitor, value);
			case MethodType value -> visitor.visitLdcInsn(fromMethodType(value));
			case MethodHandle value -> visitor.visitLdcInsn(fromMethodHandle(value));

			// ConstantDesc
			case ConstantDesc value -> {
				return visitConstantValue(visitor, resolveConstantValue(value));
			}

			// Constables
			case Constable value -> {
				final var optional = value.describeConstable();
				if (optional.isEmpty()) {
					return false;
				}
				return visitConstantValue(visitor, resolveConstantValue(optional.get()));
			}

			case null -> visitor.visitInsn(ACONST_NULL);

			// We don't understand what you want-
			default -> {
				return false;
			}
		}
		return true;
	}

	static Object resolveConstantValue(
		final ConstantDesc desc
	) {
		return switch (desc) {
			case ClassDesc value -> Type.getType(value.descriptorString());
			case MethodHandleDesc value -> fromMethodHandle(value);
			case MethodTypeDesc value -> fromMethodType(value);
			case Double value -> value;
			case Float value -> value;
			case Integer value -> value;
			case Long value -> value;
			case String value -> value;
			case DynamicConstantDesc<?> value -> {
				final var list = value.bootstrapArgs();
				final var array = new Object[list.length];
				for (int i = 0; i < array.length; i++) {
					array[i] = resolveConstantValue(list[i]);
				}
				yield new ConstantDynamic(
					value.constantName(),
					value.constantType().descriptorString(),
					fromMethodHandle(value.bootstrapMethod()),
					array
				);
			}
		};
	}

	static Type fromMethodType(final MethodType value) {
		return Type.getMethodType(Type.getType(value.returnType()), toParameterTypeArray(value));
	}

	static Type fromMethodType(final MethodTypeDesc value) {
		return Type.getMethodType(value.descriptorString());
	}

	private static Type[] toParameterTypeArray(final MethodType value) {
		final var array = new Type[value.parameterCount()];
		for (int i = 0; i < array.length; i++) {
			array[i] = Type.getType(value.parameterType(i));
		}
		return array;
	}

	static Handle fromMethodHandle(final MethodHandle value) {
		final var desc = value.describeConstable()
			.orElseThrow(() -> new AssertionError("unconstable: " + value));

		if (!(desc instanceof DirectMethodHandleDesc direct)) {
			throw new UnsupportedOperationException(value.toString());
		}

		if (!value.type().descriptorString().equals(direct.lookupDescriptor())) {
			throw new AssertionError(value + " != " + direct);
		}

		return fromMethodHandle(direct);
	}

	static Handle fromMethodHandle(final MethodHandleDesc value) {
		if (!(value instanceof DirectMethodHandleDesc)) {
			throw new UnsupportedOperationException(value.toString());
		}

		return fromMethodHandle((DirectMethodHandleDesc) value);
	}

	static Handle fromMethodHandle(final DirectMethodHandleDesc value) {
		return new Handle(
			value.refKind(),
			value.owner().descriptorString(),
			value.methodName(),
			value.lookupDescriptor(),
			value.isOwnerInterface()
		);
	}
}
