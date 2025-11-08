package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.token.IdentifierToken;
import dev.spiritstudios.mojank.token.MolangToken;
import dev.spiritstudios.mojank.token.NumberToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static dev.spiritstudios.mojank.token.NumberToken.*;
import static dev.spiritstudios.mojank.token.OperatorToken.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LexerTests {
	private static void assertTokensEqual(String expression, MolangToken... expected) throws IOException {
		var reader = new StringReader(expression);
		var lexer = new MolangLexer(reader);

		var tokens = lexer.readAll();

		assertEquals(tokens.size(), expected.length, "Different amount of tokens in tokenized result.");

		for (int i = 0; i < tokens.size(); i++) {
			var expectedToken = expected[i];
			var actual = tokens.get(i);

			assertEquals(expectedToken, actual, "Incorrect token " + actual + ", expected " + expectedToken);
		}
	}

	@Test
	public void test() throws IOException {
		assertTokensEqual(
			"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;",
			new IdentifierToken("math"), DOT, new IdentifierToken("cos"), OPENING_PAREN, new IdentifierToken("query"), DOT, new IdentifierToken("anim_time"), MULTIPLY, new NumberToken(38), CLOSING_PAREN, MULTIPLY, new IdentifierToken("variable"), DOT, new IdentifierToken("rotation_scale"), ADD, new IdentifierToken("variable"), DOT, new IdentifierToken("x"), MULTIPLY, new IdentifierToken("variable"), DOT, new IdentifierToken("x"), MULTIPLY, new IdentifierToken("query"), DOT, new IdentifierToken("life_time"), END_EXPRESSION, EOF
		);

		assertTokensEqual(
			"-(cond ? 1 : 0)",
			SUBTRACT, OPENING_PAREN, new IdentifierToken("cond"), IF, ONE, ELSE, ZERO, CLOSING_PAREN, EOF
		);
	}
}
