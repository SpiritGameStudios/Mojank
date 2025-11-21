package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Parser;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.compile.CompilerFactory;
import dev.spiritstudios.mojank.meow.link.Linker;
import dev.spiritstudios.mojank.meow.test.Context;
import dev.spiritstudios.mojank.meow.test.Functor;
import dev.spiritstudios.mojank.meow.test.MolangMath;
import dev.spiritstudios.mojank.meow.test.Query;
import dev.spiritstudios.mojank.meow.test.debug.DebugUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Assertions {
	private static final Logger logger = Util.logger();

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

	private static final Linker linker = Linker.UNTRUSTED.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Variables.class, Query.Vec3.class)
		.aliasClass(MolangMath.class, "math")
		.build();

	private static final CompilerFactory<Functor> factory = new CompilerFactory<>(lookup, Functor.class, linker);


	public static void assertEvalEquals(
		float expected,
		String source,
		Context context,
		Query query,
		boolean debug
	) throws IllegalAccessException {
		var expression = Parser.MOLANG.parse(source);

		if (debug) {
			logger.info("Expression: {}", expression);
		}

		var analyser = factory.createAnalyser();

		var time = Instant.now();
		analyser.analyse(expression);

		if (debug) {
			logger.info("Analysis took {}", Util.formatDuration(Duration.between(time, Instant.now())));
		}

		var analysis = analyser.finish(lookup);

		if (!analysis.variables().members().isEmpty() && debug) {
			time = Instant.now();
			var variablesBytecode = analyser.createVariables(lookup);
			logger.info("Variables compilation took {}", Util.formatDuration(Duration.between(time, Instant.now())));

			DebugUtils.debug(variablesBytecode);
		}

		var compiler = factory.build(analysis);

		time = Instant.now();
		var bytecode = compiler.compileToBytes(source, expression);
		if (debug) {
			logger.info("Compilation took {}", Util.formatDuration(Duration.between(time, Instant.now())));
			DebugUtils.debug(bytecode);
		}

		var program = compiler.define(bytecode);


		var resultVariables = analysis.createVariables();

		try {
			assertEquals(
				expected,
				((Functor) program).invoke(context, query, resultVariables)
			);
		} finally {
			if (debug) {
				logger.info("=> {}", resultVariables);
			}
		}
	}

	public static void assertEvalEquals(
		float expected,
		String source,
		Context context,
		Query query
	) throws IllegalAccessException {
		assertEvalEquals(expected, source, context, query, false);
	}

	public static void assertEvalEquals(
		float expected,
		String source
	) throws IllegalAccessException {
		assertEvalEquals(expected, source,false);
	}

	public static void assertEvalEquals(
		float expected,
		String source,
		boolean debug
	) throws IllegalAccessException {
		assertEvalEquals(expected, source, null, null, debug);
	}
}
