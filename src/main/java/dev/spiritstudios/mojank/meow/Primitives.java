package dev.spiritstudios.mojank.meow;

import org.glavo.classfile.Opcode;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.Map;

import static org.glavo.classfile.Opcode.*;

/**
 * @author Ampflower
 **/
public enum Primitives {
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
//
//	private static final int[] castLookup = {
//		// to byte
//		NOP, I2B, I2B, I2B, L2I, F2I, D2I,
//		// to short
//		NOP, NOP, I2S, I2S, L2I, F2I, D2I,
//		// to char
//		I2C, I2C, NOP, I2C, L2I, F2I, D2I,
//		// to int
//		NOP, NOP, NOP, NOP, L2I, F2I, D2I,
//		// to long
//		I2L, I2L, I2L, I2L, NOP, F2L, D2L,
//		// to float
//		I2F, I2F, I2F, I2F, L2F, NOP, D2F,
//		// to double
//		I2D, I2D, I2D, I2D, L2D, F2D, NOP,
//	};
//
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
//
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
//
	public static final Map<Class<?>, Primitives> lookup;

	static { // lookup
		final Map<Class<?>, Primitives> map = new HashMap<>(boxLookup);
		map.putAll(primitiveLookup);
		lookup = Map.copyOf(map);
	}

	public final Class<?> box;
	public final Class<?> primitive;
	public final Opcode loadOpcode;
	public final Opcode storeOpcode;
	public final Opcode returnOpcode;
//	public final String boxName;
//	public final String primitiveName;
	private final @Nullable String unbox;
	private final @Nullable MethodTypeDesc unboxDescriptor;
	private final @Nullable MethodTypeDesc boxDescriptor;

	Primitives(
		final Class<?> box,
		final Class<?> primitive,
		final Opcode loadOpcode,
		final Opcode storeOpcode,
		final Opcode returnOpcode,
		@Nullable final String unbox
	) {
		this.box = box;
		this.primitive = primitive;
		this.returnOpcode = returnOpcode;
		this.storeOpcode = storeOpcode;
		this.loadOpcode = loadOpcode;

		this.unbox = unbox;
		this.unboxDescriptor = unbox != null ? BoilerplateGenerator.methodDesc(primitive) : null;
		this.boxDescriptor = unbox != null ? BoilerplateGenerator.methodDesc(box, primitive) : null;
	}

	Primitives(
		final Class<?> box,
		final Class<?> primitive,
		final String unbox
	) {
		this(box, primitive, ILOAD, ISTORE, IRETURN, unbox);
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
