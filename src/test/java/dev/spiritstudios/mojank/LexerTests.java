package dev.spiritstudios.mojank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.io.StringReader;
import java.util.zip.CheckedInputStream;

import static dev.spiritstudios.mojank.MolangToken.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LexerTests {
	private static void assertTokensEqual(String expression, MolangToken.Kind... kinds) throws IOException {
		var reader = new StringReader(expression);
		var lexer = new MolangLexer(reader);

		var tokens = lexer.readAll();

		assertEquals(tokens.size(), kinds.length, "Different amount of tokens in tokenized result.");

		for (int i = 0; i < tokens.size(); i++) {
			var expected = kinds[i];
			var actual = tokens.get(i);

			assertEquals(expected, actual.kind(), "Incorrect token " + actual + ", expected " + expected);
		}
	}

	@Test
	public void test() throws IOException {
		assertTokensEqual(
			"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;",
			IDENTIFIER, DOT, IDENTIFIER, OPENING_PAREN, IDENTIFIER, DOT, IDENTIFIER, MULTIPLY, NUMBER, CLOSING_PAREN, MULTIPLY, IDENTIFIER, DOT, IDENTIFIER, ADD, IDENTIFIER, DOT, IDENTIFIER, MULTIPLY, IDENTIFIER, DOT, IDENTIFIER, MULTIPLY, IDENTIFIER, DOT, IDENTIFIER, END_EXPRESSION, EOF
		);

		assertTokensEqual(
			"-(cond ? 1 : 0)",
			SUBTRACT, OPENING_PAREN, IDENTIFIER, CONDITIONAL, NUMBER, ELSE, NUMBER, CLOSING_PAREN, EOF
		);
	}
}
