package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.MethodCallExpression;
import dev.spiritstudios.mojank.ast.ConstantExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
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
			new UnaryOperationExpression(
				new BinaryOperationExpression(
					new BinaryOperationExpression(
						new MethodCallExpression(
							new AccessExpression("math", "cos"),
							new BinaryOperationExpression(
								new AccessExpression("query", "anim_time"),
								BinaryOperationExpression.Operator.MULTIPLY,
								new ConstantExpression(38F)
							)
						),
						BinaryOperationExpression.Operator.MULTIPLY,
						AccessExpression.variable("rotation_scale")
					),
					BinaryOperationExpression.Operator.ADD,
					new BinaryOperationExpression(
						new BinaryOperationExpression(
							AccessExpression.variable("x"),
							BinaryOperationExpression.Operator.MULTIPLY,
							AccessExpression.variable("x")
						),
						BinaryOperationExpression.Operator.MULTIPLY,
						new AccessExpression("query", "life_time")
					)
				),
				UnaryOperationExpression.Operator.RETURN
			),
			parse(
				"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;"
			)
		);

		assertEquals(
			new UnaryOperationExpression(
				new UnaryOperationExpression(
					new TernaryOperationExpression(
						new AccessExpression("cond"),
						new ConstantExpression(1.0F),
						new ConstantExpression(0.0F)
					),
					UnaryOperationExpression.Operator.NUMERICAL_NEGATE
				),
				UnaryOperationExpression.Operator.RETURN
			),
			parse("-(cond ? 1 : 0)")
		);

		assertEquals(
			new UnaryOperationExpression(
				new BinaryOperationExpression(
					AccessExpression.variable("a"),
					BinaryOperationExpression.Operator.ADD,
					new UnaryOperationExpression(AccessExpression.variable("b"), UnaryOperationExpression.Operator.NUMERICAL_NEGATE)
				),
				UnaryOperationExpression.Operator.RETURN
			)
			,
			parse("variable.a+-variable.b")
		);

		assertEquals(
			new UnaryOperationExpression(
				new BinaryOperationExpression(
					AccessExpression.variable("a"),
					BinaryOperationExpression.Operator.SUBTRACT,
					new UnaryOperationExpression(AccessExpression.variable("b"), UnaryOperationExpression.Operator.POSITIVE)
				),
				UnaryOperationExpression.Operator.RETURN
			)
			,
			parse("variable.a-+variable.b")
		);

		assertEquals(
			new UnaryOperationExpression(
				new BinaryOperationExpression(
					AccessExpression.variable("a"),
					BinaryOperationExpression.Operator.ADD,
					new UnaryOperationExpression(AccessExpression.variable("b"), UnaryOperationExpression.Operator.POSITIVE)
				),
				UnaryOperationExpression.Operator.RETURN
			)
			,
			parse("variable.a++variable.b")
		);

	}
}
