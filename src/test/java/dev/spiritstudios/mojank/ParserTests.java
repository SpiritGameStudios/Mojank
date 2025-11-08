package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.ast.VariableExpression;
import dev.spiritstudios.mojank.internal.Util;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ParserTests {
	private static final Logger LOGGER = Util.logger();

	private static Expression parse(String expression) throws IOException {
		var reader = new StringReader(expression);
		var lexer = new MolangLexer(reader);
		var parser = new MolangParser(lexer);

		Expression exp = parser.parseAll();
		LOGGER.info(exp.toString());
		return exp;
	}

	@Test
	public void test() throws IOException {
		assertEquals(
			new BinaryOperationExpression(
				new BinaryOperationExpression(
					new FunctionCallExpression(
						new AccessExpression("math", "cos"),
						new BinaryOperationExpression(
							new AccessExpression("query", "anim_time"),
							BinaryOperationExpression.Operator.MULTIPLY,
							new NumberExpression(38F)
						)
					),
					BinaryOperationExpression.Operator.MULTIPLY,
					new VariableExpression("rotation_scale")
				),
				BinaryOperationExpression.Operator.ADD,
				new BinaryOperationExpression(
					new BinaryOperationExpression(
						new VariableExpression("x"),
						BinaryOperationExpression.Operator.MULTIPLY,
						new VariableExpression("x")
					),
					BinaryOperationExpression.Operator.MULTIPLY,
					new AccessExpression("query", "life_time")
				)
			),
			parse(
				"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;"
			)
		);

		assertEquals(
			new UnaryOperationExpression(
				new TernaryOperationExpression(
					new AccessExpression("cond"),
					new NumberExpression(1.0F),
					new NumberExpression(0.0F)
				),
				UnaryOperationExpression.Operator.NEGATE
			),
			parse("-(cond ? 1 : 0)")
		);
	}
}
