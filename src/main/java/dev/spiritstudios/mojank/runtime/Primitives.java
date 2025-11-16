package dev.spiritstudios.mojank.runtime;

import dev.spiritstudios.mojank.meow.compile.Primitive;

/**
 * <b><em>UNSUPPORTED</em></b>: Although 'exposed', this class will change as the compiler evolves.
 * <p>
 * Autounboxing helper as emitted by {@link Primitive} and used by {@link dev.spiritstudios.mojank.MolangInterpreter}.
 *
 * @author Ampflower
 **/
public final class Primitives {
	private Primitives() {
	}

	/**
	 * Converts a non-zero, non-NaN float to true, otherwise false.
	 */
	public static boolean castToBoolean(final float f) {
		// Considering how to do this in bytecode, it'd be:
		// fload
		// dup
		// fcmpg // NaN = 1; eq = 0
		// fload
		// fconst_0
		// fcmpg
		// ixor
		// iconst_1
		// iand
		return f == f && f != 0;
	}

	/**
	 * Converts a non-zero, non-NaN double to true, otherwise false.
	 */
	public static boolean castToBoolean(final double d) {
		// Considering how to do this in bytecode, it'd be:
		// dload
		// dup2
		// dcmpg // NaN = 1; eq = 0
		// dload
		// dconst_0
		// dcmpg
		// ixor
		// iconst_1
		// iand
		return d == d && d != 0;
	}

	// boolean requires extra rules to avoid accidental truncation
	@SuppressWarnings("unused") // Linked by the compiler
	public static boolean unboxAsBoolean(final Object object) {
		return switch (object) {
			case Boolean b -> b;
			case Character c -> c != 0;
			case Long l -> l != 0L;
			case Float f -> castToBoolean(f);
			case Double d -> castToBoolean(d);
			case Number n -> n.intValue() != 0;
			case null -> false;
			default -> throw new ClassCastException("Cannot cast " + object + " to boolean");
		};
	}

	public static boolean unboxAsBooleanLenient(final Object object) {
		return switch (object) {
			case Boolean b -> b;
			case Character c -> c != 0;
			case Long l -> l != 0L;
			case Float f -> castToBoolean(f);
			case Double d -> castToBoolean(d);
			case Number n -> n.intValue() != 0;
			case null, default -> false;
		};
	}

	@SuppressWarnings("unused") // Linked by the compiler
	public static int unboxAsInt(final Object object) {
		return switch (object) {
			case Number number -> number.intValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1 : 0;
			case null -> 0;
			default -> throw new ClassCastException("Cannot cast " + object + " to int");
		};
	}

	@SuppressWarnings("unused") // Linked by the compiler
	public static int unboxAsIntLenient(final Object object) {
		return switch (object) {
			case Number number -> number.intValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1 : 0;
			case null, default -> 0;
		};
	}

	@SuppressWarnings("unused") // Linked by the compiler
	public static long unboxAsLong(final Object object) {
		return switch (object) {
			case Number number -> number.longValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1L : 0L;
			case null -> 0L;
			default -> throw new ClassCastException("Cannot cast " + object + " to long");
		};
	}

	@SuppressWarnings("unused") // Linked by the compiler
	public static float unboxAsFloat(final Object object) {
		return switch (object) {
			case Number number -> number.floatValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1.f : 0.f;
			case null -> 0.f;
			default -> throw new ClassCastException("Cannot cast " + object + " to float");
		};
	}

	public static float unboxAsFloatLenient(final Object object) {
		return switch (object) {
			case Number number -> number.floatValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1.f : 0.f;
			case null, default -> 0.f;
		};
	}


	@SuppressWarnings("unused") // Linked by the compiler
	public static double unboxAsDouble(final Object object) {
		return switch (object) {
			case Number number -> number.doubleValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1.d : 0.d;
			case null -> 0.d;
			default -> throw new ClassCastException("Cannot cast " + object + " to double");
		};
	}

	@SuppressWarnings("unused") // Linked by the compiler
	public static double unboxAsDoubleLenient(final Object object) {
		return switch (object) {
			case Number number -> number.doubleValue();
			case Character character -> character;
			case Boolean bool -> bool ? 1.d : 0.d;
			case null, default -> 0.d;
		};
	}
}
