package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.IndentedStringBuilder;
import dev.spiritstudios.mojank.internal.Util;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static dev.spiritstudios.mojank.MolangToken.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ParserTests {
	private static void printAST(String expression) throws IOException {
		var reader = new StringReader(expression);
		var lexer = new MolangLexer(reader);
		var parser = new MolangParser(lexer);
		var builder = new IndentedStringBuilder(new StringBuilder());
		builder.newline();
		new ComplexExpression(parser.parseAll()).append(builder);
		Util.logger().info(builder.toString());
	}

	@Test
	public void test() throws IOException {
		printAST(
			"math.cos(query.anim_time * 38) * variable.rotation_scale + variable.x * variable.x * query.life_time;"
		);
	}
}
