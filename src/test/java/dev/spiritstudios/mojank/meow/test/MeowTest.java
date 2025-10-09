package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Linker;
import dev.spiritstudios.mojank.meow.MolangBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Ampflower
 **/

public class MeowTest {
	private static final Logger logger = Util.logger();

	@Test
	public void meow() {
		final var compiler = new MolangBuilder<>(MethodHandles.lookup(), Functor.class)
			.withLinker(Linker.untrusted.toBuilder()
							.addAllowedClasses(Context.class, Query.class, Variable.class)
							.build())
			.build();

		final String program = "math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;";

		final var resultA = compiler.compile(program);
		final var resultB = compiler.compile(program);

		final var resultC = compiler.compile("42;");

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

		assertEquals(resultA, resultA.get());

		assertEquals(42, resultC.get().invoke(null, null, null));
	}
}
