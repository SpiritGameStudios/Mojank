package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.compile.Compiler;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.compile.link.Linker;
import dev.spiritstudios.mojank.meow.test.Context;
import dev.spiritstudios.mojank.meow.test.Functor;
import dev.spiritstudios.mojank.meow.test.MolangMath;
import dev.spiritstudios.mojank.meow.test.Query;
import dev.spiritstudios.mojank.meow.test.debug.DebugUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Assertions {
	private static final Logger logger = Util.logger();

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

	private static final Linker linker = Linker.UNTRUSTED.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Query.Vec3.class, Object.class)
		.aliasClass(MolangMath.class, "math")
		.build();

	public static void assertEvalEquals(
		float expected,
		String source,
		Context context,
		Query query,
		boolean debug
	) throws Throwable {
		var lexer = new MolangLexer(new StringReader(source));
		var parser = new MolangParser(lexer, linker);

		var expression = parser.parseAll();

		if (debug) {
			logger.info("Expression: {}", expression);
		}

		var time = Instant.now();
		var bytecode = Compiler.compileToBytecode(lookup, linker, Functor.class, expression, source);
		if (debug) {
			logger.info("Compilation took {}", Util.formatDuration(Duration.between(time, Instant.now())));
			DebugUtils.debug(bytecode);
		}

		var program = Compiler.<Functor>define(lookup, bytecode);

		assertEquals(
			expected,
			program.invoke(context, query)
		);
	}

	public static void assertEvalEquals(
		float expected,
		String source,
		Context context,
		Query query
	) throws Throwable {
		assertEvalEquals(expected, source, context, query, false);
	}

	public static void assertEvalEquals(
		float expected,
		String source
	) throws Throwable {
		assertEvalEquals(expected, source, false);
	}

	public static void assertEvalEquals(
		float expected,
		String source,
		boolean debug
	) throws Throwable {
		assertEvalEquals(expected, source, null, null, debug);
	}
}
