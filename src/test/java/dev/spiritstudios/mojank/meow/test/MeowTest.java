package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.CompilerFactory;
import dev.spiritstudios.mojank.meow.CompilerResult;
import dev.spiritstudios.mojank.meow.Linker;
import dev.spiritstudios.mojank.meow.Variables;
import it.unimi.dsi.fastutil.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Ampflower
 **/

public class MeowTest {
	private static final Logger logger = Util.logger();

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

	private static final Linker linker = Linker.UNTRUSTED.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Variables.class, Query.Vec3.class)
		.aliasClass(MolangMath.class, "math")
		.build();


	@ParameterizedTest
	@MethodSource("factory")
	public <C, R> void meow(
		final Class<C> target,
		final CompilerFactory<C> factory,
		final BiFunction<C, Variables, R> executor,
		final String source,
		final R expected
	) throws Throwable {
		final var compiler = factory.build();

		final C program = compiler.compileAndInitialize(source);
		final var result = (CompilerResult<C>) program;
		final var supplier = compiler.finish();

		final var resultVariables = result.createVariables();

		assertProgramValidity(
			target,
			result,
			source,
			expected,
			executor.apply(program, resultVariables)
		);

		final var supplierVariables = supplier.get();

		assertProgramValidity(
			target,
			result,
			source,
			expected,
			executor.apply(program, supplierVariables)
		);

		assertNotSame(resultVariables, supplierVariables);
		//assertEquals(resultVariables, supplierVariables);
		assertSame(resultVariables.getClass(), supplierVariables.getClass());
	}

	private static <C, R> void assertProgramValidity(
		final Class<C> target,
		final CompilerResult<C> program,
		final String source,
		final R expected,
		final Object result
	) {
		assertInstanceOf(target, program);
		assertEquals(source, program.toString());
		assertEquals(source.hashCode(), program.hashCode());
		assertEquals(target, program.getType());
		assertNotNull(program.toHandle());
		assertNotNull(program.createVariables());

		// These are meant to be unique instances.
		assertNotSame(program.createVariables(), program.createVariables());

		assertEquals(expected.getClass(), result.getClass());
		assertEquals(expected, result);
	}

	public static List<Object[]> factory() {
		final var functorFactory = new CompilerFactory<>(lookup, Functor.class)
			.withLinker(linker);

		final var list = new ArrayList<Object[]>();

		var context = new Context();
		var query = new Query();

		testPrograms(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			42F * 3F - 6F / 2F * 6F,
			"42 * 3 - 6 / 2 * 6"
		);

		testPrograms(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			MolangMath.sin(query.anim_time * 1.23F),
			"math.sin(query.anim_time * 1.23)"
		);

		testPrograms(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			2F,
			"query.pos.x = 2;\n" +
				"return query.pos.x;"
		);


		testProgramPairs(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			Pair.of(
				"""
					temp.moo = math.sin(query.anim_time * 1.23);
					temp.baa = math.cos(query.life_time + 2.0);
					return temp.moo * temp.moo + temp.baa;
					""",
				Util.make(() -> {
					var moo = MolangMath.sin(query.anim_time * 1.23F);
					var baa = MolangMath.cos(query.life_time + 2F);
					return moo * moo + baa;
				})
			),
			Pair.of("", null)
		);

		testPrograms(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			MolangMath.sin(MolangMath.sin(query.anim_time * 543F * 3534F) * 1.23F),
			"""
				temp.uwu = 543 * 3534;
				variable.meow = math.sin(query.anim_time * temp.uwu);
				variable.bark = math.sin(variable.meow * 1.23);
				return variable.bark;
				"""
		);



		testPrograms(
			list,
			Functor.class,
			functorFactory,
			(functor, variables) -> functor.invoke(context, query, variables),
			(1.3F + 3.4F) + 1.3F,
			"""
				t.cow = 1.3;
				t.pig = 3.4;
				t.cow.pig = t.cow + t.pig;
				t.cow.pig.pigpig = t.cow.pig + t.cow;
				return t.cow.pig.pigpig;
				"""
		);

		return list;
	}

	private static <C, R> void testPrograms(
		final Collection<Object[]> carrier,
		final Class<C> target,
		final CompilerFactory<C> factory,
		final BiFunction<C, Variables, R> executor,
		final R expected,
		final String... programs
	) {
		for (final String program : programs) {
			carrier.add(addArgs(target, factory, executor, program, expected));
		}
	}

	@SafeVarargs
	private static <C, R> void testProgramPairs(
		final Collection<Object[]> carrier,
		final Class<C> target,
		final CompilerFactory<C> compiler,
		final BiFunction<C, Variables, R> executor,
		final Pair<String, R>... pairs
	) {
		for (final Pair<String, R> pair : pairs) {
			carrier.add(addArgs(target, compiler, executor, pair.key(), pair.value()));
		}
	}

	private static <C, R> Object[] addArgs(
		final Class<C> target,
		final CompilerFactory<C> compiler,
		final BiFunction<C, Variables, R> executor,
		final String source,
		final R expected
	) {
		return new Object[] {target, compiler, executor, source, expected};
	}
}
