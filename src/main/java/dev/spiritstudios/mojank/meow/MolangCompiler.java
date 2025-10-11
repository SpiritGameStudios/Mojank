package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.MolangLexer;
import dev.spiritstudios.mojank.MolangParser;
import dev.spiritstudios.mojank.ast.Expression;

import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public final class MolangCompiler<T> extends Compiler<T> {

	MolangCompiler(
		final MethodHandles.Lookup lookup,
		final Class<T> type,
		final Linker linker
	) {
		super(lookup, type, linker);
	}

	@Override
	@SuppressWarnings("unchecked")
	public T compile(final String program) {
		final Expression expr;
		try {
			final var reader = new StringReader(program);
			final var lexer = new MolangLexer(reader);
			final var parser = new MolangParser(lexer);

			// TODO: make generic Parser interface
			return (T) compile(program, parser.parseAll());
			/*
			Expression exp;
			while ((exp = parser.next()) != null) {
				expr.add(exp);
				logger.info("{}", exp);
			}*/
		} catch (IOException io) {
			throw new IllegalArgumentException(program, io);
		}
	}
}
