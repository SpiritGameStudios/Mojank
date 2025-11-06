package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.Compiler;
import dev.spiritstudios.mojank.meow.CompilerFactory;
import dev.spiritstudios.mojank.meow.CompilerResult;
import dev.spiritstudios.mojank.meow.DebugUtils;
import dev.spiritstudios.mojank.meow.Linker;
import dev.spiritstudios.mojank.meow.MolangCompiler;
import dev.spiritstudios.mojank.meow.MolangFactory;
import dev.spiritstudios.mojank.meow.MolangMath;
import dev.spiritstudios.mojank.meow.Variables;
import it.unimi.dsi.fastutil.Pair;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.BoilerplateGenerator.generateConstructor;
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

	private static final Linker linker = Linker.UNTRUSTED.toBuilder()
		.addAllowedClasses(Context.class, Query.class, Variables.class)
		.aliasClass(MolangMath.class, "math")
		.build();


	@ParameterizedTest
	@MethodSource("factory")
	public <C, R> void meow(
		final Class<C> target,
		final CompilerFactory<C, MolangCompiler<C>> factory,
		final BiFunction<C, Variables, R> executor,
		final String source,
		final R expected
	) throws Throwable {
		byte[][] programBytecode = new byte[1][];

		var variablesBytecode = ClassFile.of().build(
			ClassDesc.of(lookup.lookupClass().getPackage().getName(), "MeowVariables"),
			variablesBuilder -> {
				generateConstructor(variablesBuilder, ConstantDescs.CD_Object);
				variablesBuilder.withInterfaces(variablesBuilder.constantPool().classEntry(desc(Variables.class)));

				var compiler = factory.build(variablesBuilder);
				programBytecode[0] = compiler.compile(source);
			}
		);

		DebugUtils.decompile(variablesBytecode);
		DebugUtils.decompile(programBytecode[0]);

		var variablesLookup = lookup.defineHiddenClass(
			variablesBytecode,
			true
		);

		var programLookup = lookup.defineHiddenClassWithClassData(
			programBytecode[0],
			variablesLookup.lookupClass(),
			true
		);

		var variables = (Variables) variablesLookup.findConstructor(
				variablesLookup.lookupClass(),
				MethodType.methodType(void.class)
			)
			.invoke();

		var program = (C) programLookup.findConstructor(
			programLookup.lookupClass(),
			MethodType.methodType(void.class)
		).invoke();

		assertProgramValidity(
			target,
			(CompilerResult<C>) program,
			source,
			expected,
			executor.apply(program, variables)
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

		assertEquals(expected.getClass(), result.getClass());
		assertEquals(expected, result);
	}

	public static List<Object[]> factory() {
		final var functorFactory = new MolangFactory<>(lookup, Functor.class)
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
			1.23F,
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
		final CompilerFactory<C, MolangCompiler<C>> factory,
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
		final CompilerFactory<C, MolangCompiler<C>> compiler,
		final BiFunction<C, Variables, R> executor,
		final Pair<String, R>... pairs
	) {
		for (final Pair<String, R> pair : pairs) {
			carrier.add(addArgs(target, compiler, executor, pair.key(), pair.value()));
		}
	}

	private static <C, R> Object[] addArgs(
		final Class<C> target,
		final CompilerFactory<C, MolangCompiler<C>> compiler,
		final BiFunction<C, Variables, R> executor,
		final String source,
		final R expected
	) {
		return new Object[] {target, compiler, executor, source, expected};
	}
}
