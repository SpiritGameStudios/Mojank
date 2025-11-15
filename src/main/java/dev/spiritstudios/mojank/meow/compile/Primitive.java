package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.internal.Util;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.TypeKind;
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
import static org.glavo.classfile.Opcode.IFNONNULL;
import static org.glavo.classfile.TypeKind.BooleanType;
import static org.glavo.classfile.TypeKind.ByteType;
import static org.glavo.classfile.TypeKind.CharType;
import static org.glavo.classfile.TypeKind.DoubleType;
import static org.glavo.classfile.TypeKind.FloatType;
import static org.glavo.classfile.TypeKind.IntType;
import static org.glavo.classfile.TypeKind.LongType;
import static org.glavo.classfile.TypeKind.ReferenceType;
import static org.glavo.classfile.TypeKind.ShortType;
import static org.glavo.classfile.TypeKind.VoidType;

/**
 * @author Ampflower
 **/
public enum Primitive {
	/**
	 * Allows a graceful getOrDefault
	 */
	Unknown(Object.class, Object.class, ReferenceType, null) {
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
	Void(Void.class, void.class, VoidType, null) {
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
	Boolean(Boolean.class, boolean.class, BooleanType, IntType, "booleanValue"),
	Byte(Byte.class, byte.class, ByteType, IntType, "byteValue"),
	Short(Short.class, short.class, ShortType, IntType, "shortValue"),
	Character(Character.class, char.class, CharType, IntType, "charValue"),
	Integer(Integer.class, int.class, IntType, IntType, "intValue"),
	Long(Long.class, long.class, LongType, "longValue"),
	Float(Float.class, float.class, FloatType, "floatValue"),
	Double(Double.class, double.class, DoubleType, "doubleValue"),
	;

	private static final Logger logger = Util.logger();

	// @formatter:off
	private static final TypeKind[] castLookup = {
		// to boolean
		null,		null,		null,		null,		null,		IntType,	IntType,	IntType,
		// to byte
		null,		null,		ByteType,	ByteType,	ByteType,	IntType,	IntType,	IntType,
		// to short
		null,		null,		null,		ShortType,	ShortType,	IntType,	IntType,	IntType,
		// to char
		null,		CharType,	CharType,	null,		CharType,	IntType,	IntType,	IntType,
		// to int
		null,		null,		null,		null,		null,		IntType,	IntType,	IntType,
		// to long
		LongType,	LongType,	LongType,	LongType,	LongType,	null,		LongType,	LongType,
		// to float
		FloatType,	FloatType,	FloatType,	FloatType,	FloatType,	FloatType,	null,		FloatType,
		// to double
		DoubleType,	DoubleType,	DoubleType,	DoubleType,	DoubleType,	DoubleType,	DoubleType,	null,
	};
	// @formatter:on

	// @formatter:off
	/**
     * Lookup table of box classes to the Primitive value.
     */
	public static final Map<Class<?>, Primitive> boxLookup = Map.of(
		Void.class,			Void,
		Boolean.class,		Boolean,
		Byte.class,			Byte,
		Short.class,		Short,
		Character.class,	Character,
		Integer.class,		Integer,
		Long.class,			Long,
		Float.class,		Float,
		Double.class,		Double
	);
	/**
     * Lookup table of primitive classes to the Primitive value.
     */
	public static final Map<Class<?>, Primitive> primitiveLookup = Map.of(
		void.class,			Void,
		boolean.class,		Boolean,
		byte.class,			Byte,
		short.class,		Short,
		char.class,			Character,
		int.class,			Integer,
		long.class,			Long,
		float.class,		Float,
		double.class,		Double
	);
	// @formatter:on
	/**
	 * Lookup table of both primitive and box classes to Primitive value.
	 */
	public static final Map<Class<?>, Primitive> lookup;

	static { // lookup
		final Map<Class<?>, Primitive> map = new HashMap<>(boxLookup);
		map.putAll(primitiveLookup);
		lookup = Map.copyOf(map);
	}

	public final Class<?> box;
	public final Class<?> primitive;

	public final ClassDesc CD_box;
	public final ClassDesc CD_primitive;

	public final TypeKind trueType;
	public final TypeKind codeType;

	private final @Nullable String unbox;
	private final @Nullable MethodTypeDesc unboxDescriptor;
	private final @Nullable MethodTypeDesc boxDescriptor;

	Primitive(
		final Class<?> box,
		final Class<?> primitive,
		final TypeKind type,
		@Nullable final String unbox
	) {
		this(box, primitive, type, type, unbox);
	}

	Primitive(
		final Class<?> box,
		final Class<?> primitive,
		final TypeKind trueType,
		final TypeKind codeType,
		@Nullable final String unbox
	) {
		this.box = box;
		this.primitive = primitive;

		this.CD_box = desc(box);
		this.CD_primitive = desc(primitive);

		this.trueType = trueType;
		this.codeType = codeType;

		this.unbox = unbox;
		this.unboxDescriptor = unbox != null ? BoilerplateGenerator.methodDesc(primitive) : null;
		this.boxDescriptor = unbox != null ? BoilerplateGenerator.methodDesc(box, primitive) : null;
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

		final var sourcePrimitive = lookup.get(sourceType);

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

	public static void convertToPrimitive(final CodeBuilder builder, final Class<?> source, final Primitive target) {
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

		if (source == Object.class) {
			builder.checkcast(target.CD_box);
			target.unbox(builder);
			return;
		}

		logger.warn("Cannot convert {} to {}; turning into 0", source, target);

		if (source == long.class || source == double.class) {
			builder.pop2();
		} else {
			builder.pop();
		}

		switch (target) {
			case Boolean, Byte, Short, Character, Integer -> builder.iconst_0();
			case Long -> builder.lconst_0();
			case Float -> builder.fconst_0();
			case Double -> builder.dconst_0();
			case Unknown -> builder.aconst_null();
		}
	}

	public static void convertFromPrimitive(final CodeBuilder builder, final Primitive source, final Class<?> target) {
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
		final Primitive source,
		final Primitive target
	) {
		var targetType = castOpcodeOf(source, target);

		if (targetType == null) {
			return;
		}

		builder.convertInstruction(source.codeType, targetType);

		if (targetType != target.trueType) {
			targetType = castOpcodeOf(Integer, target);

			if (targetType != null) {
				builder.convertInstruction(IntType, targetType);
			}
		}
	}

	public static void downcastToBoolean(
		final CodeBuilder builder,
		final Primitive source
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
			default -> builder.ifThenElse(
				CodeBuilder::iconst_1,
				CodeBuilder::iconst_0
			);
		}
	}

	@CheckReturnValue
	public static TypeKind castOpcodeOf(
		final Class<?> sourceType,
		final Class<?> targetType
	) {
		if (
			sourceType == void.class ||
			!sourceType.isPrimitive() ||
			targetType == void.class ||
			!targetType.isPrimitive()
		) {
			return null;
		}

		return castOpcodeOf(
			primitiveLookup.get(sourceType),
			primitiveLookup.get(targetType)
		);
	}

	@CheckReturnValue
	public static TypeKind castOpcodeOf(
		final Primitive sourceType,
		final Primitive targetType
	) {
		if (
			sourceType == Void ||
			sourceType == Unknown ||
			targetType == Void ||
			targetType == Unknown
		) {
			return null;
		}

		final int s = sourceType.ordinal() - 2;
		final int t = targetType.ordinal() - 2;

		return castLookup[t * 8 + s];
	}
}
