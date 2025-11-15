package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.internal.ForbiddenStackWalker;
import dev.spiritstudios.mojank.internal.Util;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Opcode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.Map;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static org.glavo.classfile.Opcode.ALOAD;
import static org.glavo.classfile.Opcode.ARETURN;
import static org.glavo.classfile.Opcode.ASTORE;
import static org.glavo.classfile.Opcode.D2F;
import static org.glavo.classfile.Opcode.D2I;
import static org.glavo.classfile.Opcode.D2L;
import static org.glavo.classfile.Opcode.DLOAD;
import static org.glavo.classfile.Opcode.DRETURN;
import static org.glavo.classfile.Opcode.DSTORE;
import static org.glavo.classfile.Opcode.F2D;
import static org.glavo.classfile.Opcode.F2I;
import static org.glavo.classfile.Opcode.F2L;
import static org.glavo.classfile.Opcode.FLOAD;
import static org.glavo.classfile.Opcode.FRETURN;
import static org.glavo.classfile.Opcode.FSTORE;
import static org.glavo.classfile.Opcode.I2B;
import static org.glavo.classfile.Opcode.I2C;
import static org.glavo.classfile.Opcode.I2D;
import static org.glavo.classfile.Opcode.I2F;
import static org.glavo.classfile.Opcode.I2L;
import static org.glavo.classfile.Opcode.I2S;
import static org.glavo.classfile.Opcode.IFNONNULL;
import static org.glavo.classfile.Opcode.ILOAD;
import static org.glavo.classfile.Opcode.IRETURN;
import static org.glavo.classfile.Opcode.ISTORE;
import static org.glavo.classfile.Opcode.L2D;
import static org.glavo.classfile.Opcode.L2F;
import static org.glavo.classfile.Opcode.L2I;
import static org.glavo.classfile.Opcode.LLOAD;
import static org.glavo.classfile.Opcode.LRETURN;
import static org.glavo.classfile.Opcode.LSTORE;
import static org.glavo.classfile.Opcode.NOP;
import static org.glavo.classfile.Opcode.RETURN;

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

		@Override
		public void box(final CodeBuilder builder) {
			// no-op, boxing would be itself
		}

		@Override
		public void unbox(final CodeBuilder builder) {
			// no-op, unboxing would be itself
		}
	},
	// The rest are actual primitives
	Void(Void.class, void.class, NOP, NOP, RETURN, null) {
		@Override
		public boolean isCompatibleTarget(final Class<?> type) {
			return false;
		}

		@Override
		public void box(final CodeBuilder builder) {
			throw new IllegalArgumentException("void cannot be boxed");
		}

		@Override
		public void unbox(final CodeBuilder builder) {
			throw new IllegalArgumentException("Void cannot be unboxed");
		}
	},
	Boolean(Boolean.class, boolean.class, "booleanValue"),
	Byte(Byte.class, byte.class, "byteValue"),
	Short(Short.class, short.class, "shortValue"),
	Character(Character.class, char.class, "charValue"),
	Integer(Integer.class, int.class, "intValue"),
	Long(Long.class, long.class, LLOAD, LSTORE, LRETURN, "longValue"),
	Float(Float.class, float.class, FLOAD, FSTORE, FRETURN, "floatValue"),
	Double(Double.class, double.class, DLOAD, DSTORE, DRETURN, "doubleValue"),
	;

	private static final Logger logger = Util.logger();

	private static final Opcode[] castLookup = {
		// to boolean
		NOP, NOP, NOP, NOP, NOP, L2I, F2I, D2I,
		// to byte
		NOP, NOP, I2B, I2B, I2B, L2I, F2I, D2I,
		// to short
		NOP, NOP, NOP, I2S, I2S, L2I, F2I, D2I,
		// to char
		NOP, I2C, I2C, NOP, I2C, L2I, F2I, D2I,
		// to int
		NOP, NOP, NOP, NOP, NOP, L2I, F2I, D2I,
		// to long
		I2L, I2L, I2L, I2L, I2L, NOP, F2L, D2L,
		// to float
		I2F, I2F, I2F, I2F, I2F, L2F, NOP, D2F,
		// to double
		I2D, I2D, I2D, I2D, I2D, L2D, F2D, NOP,
	};

	public static final Map<Class<?>, Primitives> boxLookup = Map.of(
		Void.class, Primitives.Void,
		Boolean.class, Boolean,
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
		boolean.class, Boolean,
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

	public final ClassDesc CD_box;
	public final ClassDesc CD_primitive;

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

		this.CD_box = desc(box);
		this.CD_primitive = desc(primitive);

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

	public void box(final CodeBuilder builder) {
		builder.invokestatic(this.CD_box, "valueOf", this.boxDescriptor);
	}

	public static void box(final CodeBuilder builder, final Class<?> maybePrimitive) {
		final var primitive = primitiveLookup.get(maybePrimitive);

		if (primitive != null) {
			primitive.box(builder);
		}
	}

	public void unbox(final CodeBuilder builder) {
		builder.invokevirtual(this.CD_box, this.unbox, this.unboxDescriptor);
	}

	private void unboxWith(final CodeBuilder builder, final Class<?> source) {
		builder.invokevirtual(desc(source), this.unbox, this.unboxDescriptor);
	}

	public static void unbox(final CodeBuilder builder, final Class<?> maybeBox) {
		final var primitive = boxLookup.get(maybeBox);

		if (primitive != null) {
			primitive.unbox(builder);
		}
	}

	public static void convert(final CodeBuilder builder, final Class<?> source, final Class<?> target) {
		ForbiddenStackWalker.dumpStack();
		if (source == target || target.isAssignableFrom(source)) {
			return;
		}

		if (source == void.class) {
			throw new IllegalArgumentException("Cannot cast from void.");
		}

		if (target == void.class) {
			if (source == double.class || source == long.class) {
				builder.pop2();
			} else {
				builder.pop();
			}
			return;
		}

		final var sourceType = lookup.getOrDefault(source, Unknown);
		final var targetType = lookup.getOrDefault(target, Unknown);

		if (sourceType == targetType) {
			if (target.isPrimitive()) {
				sourceType.unbox(builder);
			} else {
				targetType.box(builder);
			}
			return;
		}

		if (source.isPrimitive() && target.isPrimitive()) {
			cast(builder, sourceType, targetType);
			return;
		}

		if (target.isPrimitive()) {
			convertToPrimitive(builder, source, targetType);
			return;
		}

		if (source.isPrimitive()) {
			convertFromPrimitive(builder, sourceType, target);
			return;
		}

		if (String.class.isAssignableFrom(target)) {
			logger.warn("Implicit string cast: {} => {}; this is probably a bug!", source, target);
			builder.invokestatic(CD_String, "valueOf", MethodTypeDesc.of(CD_String, CD_Object));
			return;
		}

		// TODO: Number -> Number conversion?

		throw new ClassCastException("Cannot convert " + source + " to " + target);
	}

	public static void convertToPrimitive(final CodeBuilder builder, final Class<?> source, final Primitives target) {
		if (Number.class.isAssignableFrom(source)) {
			switch (target) {
				case Character, Boolean -> {
					Integer.unboxWith(builder, source);
					cast(builder, Integer, target);
				}
				default -> target.unboxWith(builder, source);
			}
			return;
		}

		if (Character.class.isAssignableFrom(source)) {
			Character.unbox(builder);
			cast(builder, Character, target);
			return;
		}

		if (Boolean.class.isAssignableFrom(source)) {
			Boolean.unbox(builder);
			cast(builder, Boolean, target);
			return;
		}
		throw new ClassCastException("Cannot convert " + source + " to " + target);
	}

	public static void convertFromPrimitive(final CodeBuilder builder, final Primitives source, final Class<?> target) {
		if (String.class.isAssignableFrom(target)) {
			logger.warn("Implicit string cast: {} => {}; this is probably a bug!", source, target);
			builder.invokestatic(CD_String, "valueOf", MethodTypeDesc.of(CD_String, source.CD_primitive));
			return;
		}

		if (source == Unknown || source == Void || !target.isAssignableFrom(source.box)) {
			throw new ClassCastException("Cannot convert " + source + " to " + target);
		}
		source.box(builder);
	}

	public static void cast(
		final CodeBuilder builder,
		final Primitives source,
		final Primitives target
	) {
		/*
		if (target == Boolean) {
			downcastToBoolean(builder, source);
			return;
		}
		*/

		Opcode op = castOpcodeOf(source, target);

		if (op == NOP) {
			return;
		}

		builder.operatorInstruction(op);

		if (op != L2I && op != F2I && op != D2I) {
			return;
		}

		op = castOpcodeOf(Integer, target);
		if (op != NOP) {
			builder.operatorInstruction(op);
		}
	}

	public static void downcastToBoolean(
		final CodeBuilder builder,
		final Primitives source
	) {
		switch (source) {
			case Void -> throw new ClassCastException("Cannot cast void to boolean");
			case Unknown -> builder.ifThenElse(
				IFNONNULL,
				CodeBuilder::iconst_1,
				CodeBuilder::iconst_0
			);
			case Boolean -> {
			}
			case Long -> builder.lconst_0()
				.lcmp()
				.iconst_1()
				.iand();
			case Float -> builder.fconst_0()
				.fcmpl()
				.iconst_1()
				.iand();
			case Double -> builder.dconst_0()
				.dcmpl()
				.iconst_1()
				.iand();
			default -> {
				builder.ifThenElse(
					CodeBuilder::iconst_1,
					CodeBuilder::iconst_0
				);
			}
		}
	}

	@CheckReturnValue
	public static Opcode castOpcodeOf(
		final Class<?> sourceType,
		final Class<?> targetType
	) {
		if (
			sourceType == void.class ||
			!sourceType.isPrimitive() ||
			targetType == void.class ||
			!targetType.isPrimitive()
		) {
			return NOP;
		}

		return castOpcodeOf(
			primitiveLookup.get(sourceType),
			primitiveLookup.get(targetType)
		);
	}

	@CheckReturnValue
	public static Opcode castOpcodeOf(
		final Primitives sourceType,
		final Primitives targetType
	) {
		if (
			sourceType == Void ||
			sourceType == Unknown ||
			targetType == Void ||
			targetType == Unknown
		) {
			return NOP;
		}

		final int s = sourceType.ordinal() - 2;
		final int t = targetType.ordinal() - 2;

		return castLookup[t * 8 + s];
	}

	private static int ordinal(final Class<?> type) {
		return primitiveLookup.get(type).ordinal() - 2;
	}
}
