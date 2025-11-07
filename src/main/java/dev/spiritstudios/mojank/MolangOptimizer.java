package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;

public class MolangOptimizer {
	public static Expression optimize(Expression expression) {
		return switch (expression) {
			case ArrayAccessExpression access ->
				new ArrayAccessExpression(optimize(access.array()), optimize(access.index()));
			case BinaryOperationExpression binaryOp -> {
				var optimizedLeft = optimize(binaryOp.left());
				var optimizedRight = optimize(binaryOp.right());

				if (optimizedLeft instanceof NumberExpression(float left) && optimizedRight instanceof NumberExpression(
					float right
				)) {
					Boolean boolVal = switch (binaryOp.operator()) {
						case LOGICAL_OR -> left != 0 || right != 0;
						case LOGICAL_AND -> left != 0 && right != 0;
						case EQUAL_TO -> left == right;
						case NOT_EQUAL -> left != right;
						case LESS_THAN -> left < right;
						case GREATER_THAN -> left > right;
						case LESS_THAN_OR_EQUAL_TO -> left <= right;
						case GREATER_THAN_OR_EQUAL_TO -> left >= right;
						default -> null;
					};

					if (boolVal != null) yield new NumberExpression(boolVal ? 1F : 0F);

					Float floatVal = switch (binaryOp.operator()) {
						case ADD -> left + right;
						case SUBTRACT -> left - right;
						case MULTIPLY -> left * right;
						case DIVIDE -> left / right;
						default -> null;
					};

					if (floatVal != null) yield new NumberExpression(floatVal);
				}

				yield new BinaryOperationExpression(optimizedLeft, binaryOp.operator(), optimizedRight);
			}
			case ComplexExpression complex ->
				new ComplexExpression(complex.expressions().stream().map(MolangOptimizer::optimize).toList());
			case FunctionCallExpression call -> new FunctionCallExpression(
				optimize(call.function()),
				call.arguments().stream().map(MolangOptimizer::optimize).toList()
			);
			case TernaryOperationExpression ternary -> new TernaryOperationExpression(
				optimize(ternary.condition()),
				optimize(ternary.ifTrue()),
				optimize(ternary.ifFalse())
			);
			case UnaryOperationExpression unary -> new UnaryOperationExpression(
				optimize(unary.value()),
				unary.operator()
			);
			default -> expression;
		};
	}
}
