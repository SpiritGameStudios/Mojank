package dev.spiritstudios.mojank.meow.compile;

import org.glavo.classfile.CodeBuilder;

import java.util.Map;
import java.util.function.Consumer;

public class BuiltinOperators {
	private static final Map<Class<?>, Operators> OPERATOR_LOOKUP = Map.of(
		long.class, new Operators(CodeBuilder::ladd, CodeBuilder::lsub, CodeBuilder::lmul, CodeBuilder::ldiv, CodeBuilder::lrem, CodeBuilder::lneg),
		int.class, new Operators(CodeBuilder::iadd, CodeBuilder::isub, CodeBuilder::imul, CodeBuilder::idiv, CodeBuilder::irem, CodeBuilder::ineg),
		float.class, new Operators(CodeBuilder::fadd, CodeBuilder::fsub, CodeBuilder::fmul, CodeBuilder::fdiv, CodeBuilder::frem, CodeBuilder::fneg),
		double.class, new Operators(CodeBuilder::dadd, CodeBuilder::dsub, CodeBuilder::dmul, CodeBuilder::ddiv, CodeBuilder::drem, CodeBuilder::dneg)
	);

	public static void add(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).add.accept(builder);
	}

	public static void subtract(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).subtract.accept(builder);
	}

	public static void multiply(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).multiply.accept(builder);
	}

	public static void divide(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).divide.accept(builder);
	}

	public static void remainder(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).remainder.accept(builder);
	}

	public static void negate(Class<?> type, CodeBuilder builder) {
		OPERATOR_LOOKUP.get(type).negate.accept(builder);
	}

	private record Operators(
		Consumer<CodeBuilder> add,
		Consumer<CodeBuilder> subtract,
		Consumer<CodeBuilder> multiply,
		Consumer<CodeBuilder> divide,
		Consumer<CodeBuilder> remainder,
		Consumer<CodeBuilder> negate
	) {
	}
}
