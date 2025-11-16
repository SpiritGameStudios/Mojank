package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.KeywordExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.compile.Linker;
import dev.spiritstudios.mojank.runtime.Primitives;
import org.slf4j.Logger;

import java.lang.constant.ConstantDesc;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public final class MolangInterpreter {
	private static final Logger logger = Util.logger();

	public static ConstantDesc evaluate(Expression expression, Linker linker) {
		return switch (expression) {
			case AccessExpression accessExpression -> throw new UnsupportedOperationException();
			case ArrayAccessExpression arrayAccessExpression -> throw new UnsupportedOperationException();
			case BinaryOperationExpression binary -> switch (binary.operator()) {
				case SET -> throw new UnsupportedOperationException();
				case NULL_COALESCE -> throw new UnsupportedOperationException();
				case CONDITIONAL -> throw new UnsupportedOperationException();
				case LOGICAL_OR ->
					evaluateBoolean(binary.left(), linker) || evaluateBoolean(binary.right(), linker) ? 1F : 0F;
				case LOGICAL_AND ->
					evaluateBoolean(binary.left(), linker) && evaluateBoolean(binary.right(), linker) ? 1F : 0F;
				case EQUAL_TO ->
					Objects.equals(evaluate(binary.left(), linker), evaluate(binary.right(), linker)) ? 1F : 0F;
				case NOT_EQUAL ->
					!Objects.equals(evaluate(binary.left(), linker), evaluate(binary.right(), linker)) ? 1F : 0F;
				case LESS_THAN ->
					evaluateFloat(binary.left(), linker) < evaluateFloat(binary.right(), linker) ? 1F : 0F;
				case GREATER_THAN ->
					evaluateFloat(binary.left(), linker) > evaluateFloat(binary.right(), linker) ? 1F : 0F;
				case LESS_THAN_OR_EQUAL_TO ->
					evaluateFloat(binary.left(), linker) <= evaluateFloat(binary.right(), linker) ? 1F : 0F;
				case GREATER_THAN_OR_EQUAL_TO ->
					evaluateFloat(binary.left(), linker) >= evaluateFloat(binary.right(), linker) ? 1F : 0F;
				case ADD -> evaluateFloat(binary.left(), linker) + evaluateFloat(binary.right(), linker);
				case SUBTRACT -> evaluateFloat(binary.left(), linker) - evaluateFloat(binary.right(), linker);
				case MULTIPLY -> evaluateFloat(binary.left(), linker) * evaluateFloat(binary.right(), linker);
				case DIVIDE -> evaluateFloat(binary.left(), linker) / evaluateFloat(binary.right(), linker);
				case ARROW -> throw new UnsupportedOperationException();
			};
			case ComplexExpression complexExpression -> {
				ConstantDesc ret = null;
				final var itr = complexExpression.expressions().iterator();
				while (itr.hasNext()) {
					final var expr = itr.next();
					ret = evaluate(expr, linker);
					if (ret != null && itr.hasNext()) {
						logger.warn("Potentially unconsumed value: {} => {}", expr, ret);
					}
				}
				yield ret;
			}
			case FunctionCallExpression function -> {
				var method = linker.findMethod(function.function());
				var args = function.arguments().stream().map(arg -> evaluate(arg, linker)).toArray();

				try {
					yield (ConstantDesc) method.invoke(null, args);
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			}
			case KeywordExpression keywordExpression -> throw new UnsupportedOperationException();
			case NumberExpression number -> number.value();
			case StringExpression string -> string.value();
			case TernaryOperationExpression ternary -> evaluateBoolean(ternary.condition(), linker) ?
				evaluate(ternary.ifTrue(), linker) :
				evaluate(ternary.ifFalse(), linker);
			case UnaryOperationExpression unary -> switch (unary.operator()) {
				case NEGATE -> -evaluateFloat(unary.value(), linker);
				case POSITIVE, RETURN -> evaluateFloat(unary.value(), linker);
				case LOGICAL_NEGATE -> evaluateBoolean(unary.value(), linker) ? 0F : 1F;
			};
		};
	}

	public static boolean evaluateBoolean(Expression expression, Linker linker) {
		return Primitives.unboxAsBooleanLenient(evaluate(expression, linker));
	}

	public static float evaluateFloat(Expression expression, Linker linker) {
		return Primitives.unboxAsFloatLenient(evaluate(expression, linker));
	}
}
