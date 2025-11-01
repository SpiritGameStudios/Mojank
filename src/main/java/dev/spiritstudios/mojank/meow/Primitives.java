package dev.spiritstudios.mojank.meow;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.D2F;
import static org.objectweb.asm.Opcodes.D2I;
import static org.objectweb.asm.Opcodes.D2L;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.F2D;
import static org.objectweb.asm.Opcodes.F2I;
import static org.objectweb.asm.Opcodes.F2L;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.I2B;
import static org.objectweb.asm.Opcodes.I2C;
import static org.objectweb.asm.Opcodes.I2D;
import static org.objectweb.asm.Opcodes.I2F;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.I2S;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.L2D;
import static org.objectweb.asm.Opcodes.L2F;
import static org.objectweb.asm.Opcodes.L2I;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * @author Ampflower
 **/
enum Primitives {
	/**
	 * Allows a graceful getOrDefault
	 */
	Unknown(Object.class, Object.class, ALOAD, ASTORE, ARETURN, null) {
		@Override
		public boolean isCompatibleTarget(final Class<?> type) {
			return type.isAssignableFrom(Object.class);
		}
	},
	// The rest are actual primitives
	Void(Void.class, void.class, NOP, NOP, RETURN, null) {
		@Override
		public boolean isCompatibleTarget(final Class<?> type) {
			return false;
		}
	},
	Byte(Byte.class, byte.class, "byteValue"),
	Short(Short.class, short.class, "shortValue"),
	Character(Character.class, char.class, "charValue"),
	Integer(Integer.class, int.class, "intValue"),
	Long(Long.class, long.class, LLOAD, LSTORE, LRETURN, "longValue"),
	Float(Float.class, float.class, FLOAD, FSTORE, FRETURN, "floatValue"),
	Double(Double.class, double.class, DLOAD, DSTORE, DRETURN, "doubleValue"),
	;

	private static final int[] castLookup = {
		// to byte
		NOP, I2B, I2B, I2B, L2I, F2I, D2I,
		// to short
		I2S, NOP, I2S, I2S, L2I, F2I, D2I,
		// to char
		I2C, I2C, NOP, I2C, L2I, F2I, D2I,
		// to int
		NOP, NOP, NOP, NOP, L2I, F2I, D2I,
		// to long
		I2L, I2L, I2L, I2L, NOP, F2L, D2L,
		// to float
		I2F, I2F, I2F, I2F, L2F, NOP, D2F,
		// to double
		I2D, I2D, I2D, I2D, L2D, F2D, NOP,
	};

	public static final Map<Class<?>, Primitives> boxLookup = Map.of(
		Void.class, Primitives.Void,
		Byte.class, Primitives.Byte,
		Short.class, Primitives.Short,
		Character.class, Primitives.Character,
		Integer.class, Primitives.Integer,
		Long.class, Primitives.Long,
		Float.class, Primitives.Float,
		Double.class, Primitives.Double
	);

	public static final Map<Class<?>, Primitives> primitiveLookup = Map.of(
		void.class, Primitives.Void,
		byte.class, Primitives.Byte,
		short.class, Primitives.Short,
		char.class, Primitives.Character,
		int.class, Primitives.Integer,
		long.class, Primitives.Long,
		float.class, Primitives.Float,
		double.class, Primitives.Double
	);

	public static final Map<Class<?>, Primitives> lookup;

	static { // lookup
		final Map<Class<?>, Primitives> map = new HashMap<>(boxLookup);
		map.putAll(primitiveLookup);
		lookup = Map.copyOf(map);
	}

	public final Class<?> box;
	public final Class<?> primitive;
	public final int loadOpcode;
	public final int storeOpcode;
	public final int returnOpcode;
	public final String boxName;
	public final String primitiveName;
	private final @Nullable String unbox;
	private final @Nullable String unboxDescriptor;
	private final @Nullable String boxDescriptor;

	Primitives(
		final Class<?> box,
		final Class<?> primitive,
		final int loadOpcode,
		final int storeOpcode,
		final int returnOpcode,
		@Nullable final String unbox
	) {
		this.box = box;
		this.primitive = primitive;
		this.returnOpcode = returnOpcode;
		this.storeOpcode = storeOpcode;
		this.loadOpcode = loadOpcode;
		this.boxName = Type.getInternalName(box);
		this.primitiveName = Type.getInternalName(primitive);

		this.unbox = unbox;
		this.unboxDescriptor = unbox != null ? Jit.getMethodDescriptor(primitive) : null;
		this.boxDescriptor = unbox != null ? Jit.getMethodDescriptor(box, primitive) : null;
	}

	Primitives(
		final Class<?> box,
		final Class<?> primitive,
		final String unbox
	) {
		this(box, primitive, ILOAD, ISTORE, IRETURN, unbox);
	}

	public boolean boxable() {
		return unbox != null;
	}

	public void emitBox(final MethodVisitor visitor) {
		// Void and Unknown cannot be boxed and thus should not emit these.
		if (this.boxable()) {
			visitor.visitMethodInsn(INVOKESTATIC, boxName, "valueOf", boxDescriptor, false);
		}
	}

	public void emitUnbox(final MethodVisitor visitor) {
		// Void and Unknown cannot be boxed and thus should not emit these.
		if (this.boxable()) {
			visitor.visitMethodInsn(INVOKEVIRTUAL, boxName, unbox, unboxDescriptor, false);
		}
	}

	@CheckReturnValue
	public static int returnOpcodeOf(final Class<?> clazz) {
		return primitiveLookup.getOrDefault(clazz, Unknown).returnOpcode;
	}

	@CheckReturnValue
	public static int loadOpcodeOf(final Class<?> clazz) {
		return primitiveLookup.getOrDefault(clazz, Unknown).loadOpcode;
	}

	@CheckReturnValue
	public static int storeOpcodeOf(final Class<?> clazz) {
		return primitiveLookup.getOrDefault(clazz, Unknown).storeOpcode;
	}

	@CheckReturnValue
	static int castOpcodeOf(
		final Class<?> sourceType,
		final Class<?> targetType
	) {
		if (sourceType == void.class
			|| !sourceType.isPrimitive()
			|| targetType == void.class
			|| !targetType.isPrimitive()
		) {
			return NOP;
		}

		final int s = ordinal(sourceType);
		final int t = ordinal(targetType);

		return castLookup[t * 7 + s];
	}

	private static int ordinal(final Class<?> type) {
		return primitiveLookup.get(type).ordinal() - 2;
	}

	public boolean isCompatibleTarget(
		final Class<?> type
	) {
		return type != void.class && (type.isAssignableFrom(box) || type.isPrimitive());
	}

	public static boolean isCompatible(
		final Class<?> sourceType,
		final Class<?> targetType
	) {
		if (targetType.isAssignableFrom(sourceType)) {
			return true;
		}

		final var sourcePrimitive = Primitives.lookup.get(sourceType);

		if (sourcePrimitive == null) {
			return false;
		}

		return sourcePrimitive.isCompatibleTarget(targetType);
	}
}
