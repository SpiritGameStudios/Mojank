package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Parser;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.compile.CompilerFactory;
import dev.spiritstudios.mojank.meow.compile.CompilerResult;
import dev.spiritstudios.mojank.meow.compile.Linker;
import dev.spiritstudios.mojank.meow.test.debug.DebugUtils;
import it.unimi.dsi.fastutil.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

	private static final CompilerFactory<Functor> factory = new CompilerFactory<>(lookup, Functor.class, linker);

	private static final String
		STR_FLOAT_A = "0.000000000000000000000000000000000000000000043",
		STR_FLOAT_B = "0.000000000000000000000000000000000000000001347";

	private static final int
		INT_RAW_A = 31,
		INT_RAW_B = 31 * 31;

	private static final float
		FLOAT_A = Float.parseFloat(STR_FLOAT_A),
		FLOAT_B = Float.parseFloat(STR_FLOAT_B);

	@Test
	public void variableEqualityStressTestChapter1() throws ReflectiveOperationException {
		final var factory = new CompilerFactory<>(lookup, Functor.class, linker);

		logger.debug("a => {}; b => {}", FLOAT_A, FLOAT_B);

		// They need to be bit for bit identical. If not, the rest of the test will fail.
		assertEquals(INT_RAW_A, Float.floatToRawIntBits(FLOAT_A));
		assertEquals(INT_RAW_B, Float.floatToRawIntBits(FLOAT_B));

		final var analyser = factory.createAnalyser();

		final var exprA = Parser.MOLANG.parse("v.a = " + STR_FLOAT_A + "; return 0");
		final var exprB = Parser.MOLANG.parse("v.b = " + STR_FLOAT_B + "; return 0");

		analyser.analyse(exprA);
		analyser.analyse(exprB);

		final var result = analyser.finish(lookup);
		final var compiler = factory.build(result);

		final var resultA = compiler.compileAndInitialize(exprA, "v.a = " + STR_FLOAT_A + "; return 0");
		final var resultB = compiler.compileAndInitialize(exprB, "v.b = " + STR_FLOAT_B + "; return 0");

		// TODO: consider allowing bodging into variables.

		final var variableA = result.createVariables();
		final var variableB = result.createVariables();
		final var variableC = result.createVariables();

		// ensure they are the same initial vector
		assertVariables(variableA, variableB);
		assertVariables(variableA, variableC);

		resultA.invoke(null, null, variableA);
		resultB.invoke(null, null, variableB);

		// compound variables
		resultA.invoke(null, null, variableC);
		resultB.invoke(null, null, variableC);

		// if they are the same, something has gone wrong.
		assertNotEquals(variableA, variableB);
		assertNotEquals(variableA.toString(), variableB.toString());

		assertNotEquals(variableA, variableC);
		assertNotEquals(variableA.toString(), variableC.toString());

		assertNotEquals(variableB, variableC);
		assertNotEquals(variableB.toString(), variableC.toString());

		// This tests for failing to run iadd after imul.
		assertNotEquals(0, variableA.hashCode(), variableA::toString);
		assertNotEquals(0, variableB.hashCode(), variableB::toString);
		assertNotEquals(0, variableC.hashCode(), variableC::toString);

		assertEquals(INT_RAW_A * 31, variableA.hashCode(), variableA::toString);
		assertEquals(INT_RAW_B, variableB.hashCode(), variableB::toString);

		// And to top it all off, ensure that this is as expected.
		assertEquals(INT_RAW_A * 31 + INT_RAW_B, variableC.hashCode(), variableC::toString);

		// A hashcode collision is hardcoded.
		// If there is not a collision, something has gone wrong.
		// This means either the prime, the iadd or imul are missing, changed, or in the wrong order.
		assertEquals(variableA.hashCode(), variableB.hashCode());
	}

	@Test
	public void variableEqualityStressTestChapter2() throws ReflectiveOperationException {
		final var factory = new CompilerFactory<>(lookup, Functor.class, linker);

		final var analyser = factory.createAnalyser();

		final var exprA = Parser.MOLANG.parse("v.a = " + STR_FLOAT_B + "; return 0");
		final var exprB = Parser.MOLANG.parse("variable.a");

		analyser.analyse(exprA);
		analyser.analyse(exprB);

		final var result = analyser.finish(lookup);
		final var compiler = factory.build(result);

		// Hi, I hope you enjoy figuring this one out.
		final var resultA = compiler.compileAndInitialize(exprA, "v.a = " + STR_FLOAT_B + "; return 0");
		final var resultB = compiler.compileAndInitialize(exprB, "variable.a");

		final var sample = result.createVariables();

		resultA.invoke(null, null, sample);

		assertEquals(FLOAT_B, resultB.invoke(null, null, sample), sample::toString);
	}

	@Test
	public void variableEqualityStressTestChapter3() throws ReflectiveOperationException {
		final var factory = new CompilerFactory<>(lookup, Functor.class, linker);

		final var analyser = factory.createAnalyser();

		final var exprA = Parser.MOLANG.parse("variable.a = " + STR_FLOAT_B + "; return 0");
		final var exprB = Parser.MOLANG.parse("v.a");

		analyser.analyse(exprA);
		analyser.analyse(exprB);

		final var result = analyser.finish(lookup);
		final var compiler = factory.build(result);

		// Hi, I hope you enjoy figuring this one out.
		final var resultA = compiler.compileAndInitialize(exprA, "variable.a = " + STR_FLOAT_B + "; return 0");
		final var resultB = compiler.compileAndInitialize(exprB, "v.a");

		final var sample = result.createVariables();

		resultA.invoke(null, null, sample);

		assertEquals(FLOAT_B, resultB.invoke(null, null, sample), sample::toString);
	}

	@ParameterizedTest(name = "\"{3}\" equals {4}")
	@MethodSource("factory")
	public <C, R> void testSingle(
		final Class<C> target,
		final CompilerFactory<C> factory,
		final BiFunction<C, Variables, R> executor,
		final String source,
		final R expected
	) throws Throwable {
		var expression = Parser.MOLANG.parse(source);



		var analyser = factory.createAnalyser();

		var time = Instant.now();
		analyser.analyse(expression);
		logger.info("Analysis took {}", Util.formatDuration(Duration.between(time, Instant.now())));

		var analysis = analyser.finish(lookup);

		if (!analysis.variables().members().isEmpty()) {
			time = Instant.now();
			var variablesBytecode = analyser.createVariables(lookup);
			logger.info("Variables compilation took {}", Util.formatDuration(Duration.between(time, Instant.now())));

			DebugUtils.debug(variablesBytecode);
		}

		var compiler = factory.build(analysis);

		time = Instant.now();
		var bytecode = compiler.compileToBytes(source, expression);
		logger.info("Compilation took {}", Util.formatDuration(Duration.between(time, Instant.now())));

		var program = compiler.define(bytecode);

		DebugUtils.debug(bytecode);

		var resultVariables = analysis.createVariables();

		assertProgramValidity(
			target,
			program,
			source,
			expected,
			executor.apply((C) program, resultVariables)
		);
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

//		assertVariables(
//			program.createVariables(),
//			program.createVariables()
//		);

		assertEquals(expected.getClass(), result.getClass());
		assertEquals(expected, result);
	}

	private static void assertVariables(
		final Variables a,
		final Variables b
	) {
		assertNotNull(a);
		assertNotNull(b);

		// These are meant to be unique instances.
		assertNotSame(a, b);
		// However, these should be the same initial value.
		assertEquals(a, b);
		// Of course, make sure they're the same variables.
		assertSame(a.getClass(), b.getClass());

		// And these two should be stable.
		assertEquals(a.hashCode(), b.hashCode());

		assertEquals(a.toString(), b.toString());

		logger.info("Manual validation:\n\ta -> {} @ {}\n\tb -> {} @ {}", a, a.hashCode(), b, b.hashCode());
	}

	public static List<Object[]> factory() {
		final var list = new ArrayList<Object[]>();

		var context = new Context();
		var query = new Query();

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			42F * 3F - 6F / 2F * 6F,
			"42 * 3 - 6 / 2 * 6"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			MolangMath.sin(query.anim_time * 1.23F),
			"math.sin(query.anim_time * 1.23)"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			2F,
			"query.pos.x = 2;\n" +
				"return query.pos.x;"
		);


		testProgramPairs(
			list,
			Functor.class,
			factory,
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
			Pair.of("", 0F)
		);

		testPrograms(
			list,
			Functor.class,
			factory,
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
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F, //true
			"""
				temp.a = 543 * 354.343;
				variable.b = 1.5;
				variable.c = math.sin(temp.a);
				variable.d = math.cos(temp.a+variable.b*math.pi);
				variable.e = variable.d == variable.c;
				return variable.e;
				"""
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F, //true
			"""
				temp.~ = 543 * 354.343;
				variable.: = 1.5;
				variable.\\ = math.sin(temp.~);
				variable.ß = math.cos(temp.~+variable.:*math.pi);
				variable.□ = variable.ß == variable.\\;
				return variable.□;
				"""
		);

		testProgramPairs(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			Pair.of("query.array_test[0]", 1F),
			Pair.of("query.array_test[0.99]", 1F), // 0.99 should round down to 0
			Pair.of("query.array_test[1]", 2F),
			Pair.of("query.array_test[1.99]", 2F), // 1.99 should round down to 1
			Pair.of("query.array_test[2]", 4F)
		);

		// And to conclude the VariableEqualityStressTest series, here's the final chapter. Chapter 4.
		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1.3F,
			"""
				v.a.b = 1.3;
				v.b = v.a;

				return v.b.b;
				"""
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1.3F,
//			"v.a = 1.3",
			"v.a = 1.3; return v.a",
			"variable.a = 1.3; return variable.a",
			"v.a = 1.3; return variable.a",
			"variable.a = 1.3; return v.a",
//			"temp.a = 1.3",
			"t.a = 1.3; return t.a",
			"temp.a = 1.3; return temp.a",
			"t.a = 1.3; return temp.a",
			"temp.a = 1.3; return t.a"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F + 10F,
			"""
				t.x = 1;
				loop(10, {
				  t.x = t.x + 1;
				});
				return t.x;
				"""
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			10F,
			"""
				t.x = 1;
				loop(20, {
				  t.x = t.x + 1;
				  t.x == 10 ? break;
				});
				t.y = t.x;

				return t.y;
				""" // MANUAL VERIFICATION: Check that slot 5 is reused for both the loop index AND y
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			0F,
			"!true"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F,
			"!false"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F,
			"""
				t.x = 1;
				return t.x == 1;
				""",
			"""
				t.x = 1;
				return t.x != 0;
				""",
			"""
				t.x = 1;
				return t.x > 0;
				""",
			"""
				t.x = 0;
				return t.x >= 0;
				"""
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F,
			"""
				t.x = 1;
				return t.x > 0 ? 1 : 0;
				""",
			"""
				t.x = 1;
				return t.x < 0 ? 0 : 1;
				""",
			"""
				t.x = 0;
				return t.x <= 0 ? 1 : 0;
				""",
			"""
				t.x = 0;
				return t.x >= 0 ? 1 : 0;
				""",
			"""
				t.x = 1;
				return t.x >= 0 ? 1 : 0;
				"""
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F,
			"query.test_bool || query.test_bool2",
			"query.test_bool || query.test_bool || query.test_bool2",
			"!(query.test_bool || query.test_bool || query.test_bool)"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			1F,
			"query.test_bool2 && query.test_bool2",
			"query.test_bool2 && !query.test_bool",
			"!(query.test_bool2 && true && false)"
		);

		testPrograms(
			list,
			Functor.class,
			factory,
			(functor, variables) -> functor.invoke(context, query, variables),
			2F,
			"query.test_bool ? 5 : 2",
			"query.test_bool2 ? 2 : 5"
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
