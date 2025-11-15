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

	public static IdentifierToken id(String value)
	{
		return new IdentifierToken(value);
	}

	public static NumberToken nt(float value)
	{
		return new NumberToken(value);
	}

	@Test
	public void test() throws IOException {
		assertTokensEqual(
			"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;",
			id("math"), DOT, id("cos"), OPENING_PAREN, id("query"), DOT, id("anim_time"), MULTIPLY, nt(38), CLOSING_PAREN, MULTIPLY, id("variable"), DOT, id("rotation_scale"), ADD, id("variable"), DOT, id("x"), MULTIPLY, id("variable"), DOT, id("x"), MULTIPLY, id("query"), DOT, id("life_time"), END_EXPRESSION, EOF
		);
		assertTokensEqual(
			"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;",
			id("math"), DOT, id("cos"), OPENING_PAREN, id("query"), DOT, id("anim_time"), MULTIPLY, nt(38), CLOSING_PAREN, MULTIPLY, id("variable"), DOT, id("rotation_scale"), ADD, id("variable"), DOT, id("x"), MULTIPLY, id("variable"), DOT, id("x"), MULTIPLY, id("query"), DOT, id("life_time"), END_EXPRESSION, EOF
		);
		assertTokensEqual(
			"-(cond ? 1 : 0)",
			SUBTRACT, OPENING_PAREN, id("cond"), IF, ONE, ELSE, ZERO, CLOSING_PAREN, EOF
		);
	}
}
