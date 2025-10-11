package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Compiler;
import dev.spiritstudios.mojank.meow.CompilerResult;
import dev.spiritstudios.mojank.meow.Linker;
import dev.spiritstudios.mojank.meow.MolangBuilder;
import it.unimi.dsi.fastutil.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Ampflower
 **/

public class MeowTest {
	private static final Logger logger = Util.logger();

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

	private final Linker linker = Linker.untrusted.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Variable.class)
		.aliasClass(Math.class, "math")
		.build();

	@Test
	public void meow() {
		final var compiler = new MolangBuilder<>(lookup, Functor.class)
			.withLinker(linker)
			.build();

		final String program = "math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;";

		final var resultA = (CompilerResult<Functor>) compiler.compile(program);
		final var resultB = (CompilerResult<Functor>) compiler.compile(program);

		final var resultC = (CompilerResult<Functor>) compiler.compile("42;");

		final var handleC = resultC.toHandle();

		logger.debug("{} => {}; {}", resultC, handleC, handleC.type());

		assertEquals(compiler, resultA.getCompiler());
		assertNotNull(resultA.toHandle());
		assertEquals(program.hashCode(), resultA.hashCode());
		assertEquals(program, resultA.toString());
		assertEquals(Functor.class, resultA.getType());

		assertEquals(resultA, resultA, "self equality");
		assertEquals(resultA, resultB, "same program");
		assertEquals(resultB, resultA, "same program");

		assertNotEquals(resultA, resultC, "diff program A");
		assertNotEquals(resultB, resultC, "diff program B");

		assertNotEquals(program, resultA, "str program A");
		assertNotEquals(program, resultB, "str program B");

		assertNotEquals("42;", resultC);
		assertNotEquals(new Object(), resultC);
		assertNotEquals(null, resultC);

		assertEquals(42, ((Functor) resultC).invoke(null, null, null));
	}

	@ParameterizedTest
	@MethodSource("factory")
	public <C, R> void meow(
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final String source,
		final R expected
	) {
		final var program = compiler.compile(source);

		assertProgramValidity(
			target,
			compiler,
			(CompilerResult<C>) program,
			source,
			expected,
			executor.apply(program)
		);
	}

	private static <C, R> void assertProgramValidity(
		final Class<C> target,
		final Compiler<C> compiler,
		final CompilerResult<C> program,
		final String source,
		final R expected,
		final Object result
	) {
		assertInstanceOf(target, program);
		assertEquals(compiler, program.getCompiler());
		assertEquals(source, program.toString());
		assertEquals(source.hashCode(), program.hashCode());
		assertEquals(target, program.getType());
		assertNotNull(program.toHandle());

		if (expected == null) {
			assertNull(result);
		} else {
			assertEquals(expected.getClass(), result.getClass());
			assertEquals(expected, result);
		}
	}

	public static Object[][] factory() {
		final var compiler = new MolangBuilder<>(lookup, Supplier.class)
			.withLinker(Linker.untrusted)
			.build();

		return meowArgs(
			Supplier.class,
			compiler,
			Supplier::get,
			Pair.of("42", 42F),
			Pair.of("'42'", "42")
		);
	}

	@SafeVarargs
	private static <C, R> Object[][] meowArgs(
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final Pair<String, R>... pairs
	) {
		final var array = new Object[pairs.length][];
		for (int i = 0; i < pairs.length; i++) {
			array[i] = meowArgs(target, compiler, executor, pairs[i].key(), pairs[i].value());
		}
		return array;
	}

	private static <C, R> Object[] meowArgs(
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final String source,
		final R expected
	) {
		return new Object[]{target, compiler, executor, source, expected};
	}
}
