package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.MolangLexer;
import dev.spiritstudios.mojank.MolangParser;
import dev.spiritstudios.mojank.ast.Expression;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * @author Ampflower
 **/
@FunctionalInterface
public interface Parser {
	Parser MOLANG = program -> {
		final var lexer = new MolangLexer(program);
		final var parser = new MolangParser(lexer);
		return parser.parseAll();
	};

	Expression parse(final Reader program) throws IOException;

	default Expression parse(final String program) {
		try (final var reader = new StringReader(program)) {
			return this.parse(reader);
		} catch (IOException exception) {
			throw new IllegalArgumentException(program, exception);
		}
	}
}
