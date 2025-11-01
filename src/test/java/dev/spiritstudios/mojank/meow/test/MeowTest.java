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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Ampflower
 **/

public class MeowTest {
	private static final Logger logger = Util.logger();

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

	private final Linker linker = Linker.UNTRUSTED.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Variable.class)
		.aliasClass(Math.class, "math")
		.build();

	@Test
	public void meow() {
		final var compiler = new MolangBuilder<>(lookup, Functor.class)
			.withLinker(linker)
			.build();

		final String program = "-math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;";

		// Casts are to access internal compile data
		@SuppressWarnings("unchecked") final var resultA = (CompilerResult<Functor>) compiler.compile(program);
		@SuppressWarnings("unchecked") final var resultB = (CompilerResult<Functor>) compiler.compile(program);

		@SuppressWarnings("unchecked") final var resultC = (CompilerResult<Functor>) compiler.compile("42 * 3 - 6 / 2 * 6");

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

		assertEquals(42 * 3 - 6F / 2F * 6, ((Functor) resultC).invoke(
			new Context(), new Query(), new Variable()
		));
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

		assertEquals(expected.getClass(), result.getClass());
		assertEquals(expected, result);
	}

	public static List<Object[]> factory() {
		final var supplierCompiler = new MolangBuilder<>(lookup, Supplier.class)
			.withLinker(Linker.TRUSTED)
			.build();

		final var functorCompiler = new MolangBuilder<>(lookup, Functor.class)
			.withLinker(Linker.TRUSTED)
			.build();

		final var list = new ArrayList<Object[]>();

		testProgramPairs(
			list,
			Supplier.class,
			supplierCompiler,
			Supplier::get,
			Pair.of("42", 42F),
			Pair.of("'42'", "42")
		);

		var context = new Context();
		var query = new Query();
		var variable = new Variable();

		testPrograms(
			list,
			Functor.class,
			functorCompiler,
			(Functor functor) -> functor.invoke(context, query, variable),
			42F * 3F - 6F / 2F * 6F,
			"42 * 3 - 6 / 2 * 6"
		);

		testProgramPairs(
			list,
			Functor.class,
			functorCompiler,
			(Functor functor) -> functor.invoke(null, null, null),
			Pair.of("""
						temp.moo = math.sin(query.anim_time * 1.23);
						temp.baa = math.cos(query.life_time + 2.0);
						return temp.moo * temp.moo + temp.baa;
						""", -1),
			Pair.of("", null)
		);

		testPrograms(
			list,
			Functor.class,
			functorCompiler,
			functor -> functor.invoke(null, null, null),
			1.23F,
			"v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; return v.cowcow.friend->v.test.a.b.c;",
			"v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; v.moo = v.cowcow.friend->v.test; return v.moo.a.b.c;",
			"v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; v.moo = v.cowcow.friend->v.test.a; return v.moo.b.c;",
			"v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; v.moo = v.cowcow.friend->v.test.a.b; return v.moo.c;",
			"v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; v.moo = v.cowcow.friend->v.test.a.b.c; return v.moo;"
		);

		return list;
	}

	private static <C, R> void testPrograms(
		final Collection<Object[]> carrier,
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final R expected,
		final String... programs
	) {
		for (final String program : programs) {
			carrier.add(addArgs(target, compiler, executor, program, expected));
		}
	}

	@SafeVarargs
	private static <C, R> void testProgramPairs(
		final Collection<Object[]> carrier,
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final Pair<String, R>... pairs
	) {
		for (final Pair<String, R> pair : pairs) {
			carrier.add(addArgs(target, compiler, executor, pair.key(), pair.value()));
		}
	}

	private static <C, R> Object[] addArgs(
		final Class<C> target,
		final Compiler<C> compiler,
		final Function<C, R> executor,
		final String source,
		final R expected
	) {
		return new Object[]{target, compiler, executor, source, expected};
	}
}
