package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.IdentifierExpression;
import dev.spiritstudios.mojank.ast.IndentedStringBuilder;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.internal.Util;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static dev.spiritstudios.mojank.MolangToken.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ParserTests {
	private static Expression parse(String expression) throws IOException {
		var reader = new StringReader(expression);
		var lexer = new MolangLexer(reader);
		var parser = new MolangParser(lexer);

		return parser.parseAll();
	}

	@Test
	public void test() throws IOException {
		assertEquals(
			new BinaryOperationExpression(
				new BinaryOperationExpression(
					new FunctionCallExpression(
						new AccessExpression("math", "cos"),
						new BinaryOperationExpression(
							new AccessExpression(new IdentifierExpression("query"), "anim_time"),
							BinaryOperationExpression.Operator.MULTIPLY,
							new NumberExpression(38F)
						)
					),
					BinaryOperationExpression.Operator.MULTIPLY,
					new AccessExpression("variable", "rotation_scale")
				),
				BinaryOperationExpression.Operator.ADD,
				new BinaryOperationExpression(
					new BinaryOperationExpression(
						new AccessExpression("variable", "x"),
						BinaryOperationExpression.Operator.MULTIPLY,
						new AccessExpression("variable", "x")
					),
					BinaryOperationExpression.Operator.MULTIPLY,
					new AccessExpression("query", "life_time")
				)
			),
			parse(
				"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;"
			)
		);
	}
}
