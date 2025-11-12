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
import dev.spiritstudios.mojank.ast.VariableExpression;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.compile.Linker;
import dev.spiritstudios.mojank.token.ErrorToken;
import dev.spiritstudios.mojank.token.IdentifierToken;
import dev.spiritstudios.mojank.token.MolangToken;
import dev.spiritstudios.mojank.token.NumberToken;
import dev.spiritstudios.mojank.token.OperatorToken;
import dev.spiritstudios.mojank.token.StringToken;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.spiritstudios.mojank.token.OperatorToken.*;

public class MolangParser {
	private static final Logger LOGGER = Util.logger();

	private final MolangLexer lexer;
	private MolangToken token;

	public MolangParser(MolangLexer lexer) throws IOException {
		this.lexer = lexer;
	}

	private void nextToken() throws IOException {
		token = lexer.next();
//		LOGGER.info(token.toString());
	}

	public Expression next() throws IOException {
		nextToken();

		switch (token) {
			case OperatorToken.EOF -> {
				return null;
			}
			case ErrorToken error -> {
				throw error.value();
			}
			default -> {
				Expression expression = parse(-1);
				if (token != OperatorToken.EOF && token != OperatorToken.END_EXPRESSION) {
					throw new RuntimeException("Expected EOF or semicolon, got " + token);
				}

				return expression;
			}
		}
	}

	public Expression parseAll() throws IOException {
		var result = new ArrayList<Expression>();

		var expression = next();
		while (expression != null) {
			result.add(expression);
			expression = next();
		}

		if (result.size() == 1) {
			return MolangOptimizer.optimize(new UnaryOperationExpression(result.getFirst(), UnaryOperationExpression.Operator.RETURN));
		} else {
			var implicitReturn = result.removeLast();
			result.add(new UnaryOperationExpression(implicitReturn, UnaryOperationExpression.Operator.RETURN));

			return MolangOptimizer.optimize(new ComplexExpression(result));
		}
	}

	private Expression parse(int lastPrecedence) throws IOException {
		Expression expression = parseSingleExpression();

		while (true) {
			if (token == EOF || token == END_EXPRESSION) {
				return expression;
			}

			Expression continuation = parseContinuation(expression, lastPrecedence);

			if (token == EOF || token == END_EXPRESSION) {
				return continuation;
			}

			if (continuation.equals(expression)) {
				return expression;
			}

			expression = continuation;
		}
	}

	private Expression parseContinuation(Expression left, int lastPrecedence) throws IOException {
		return switch (token) {
			case EOF, CLOSING_PAREN -> left;
			case OPENING_PAREN -> {
				List<Expression> args = new ArrayList<>(1);
				nextToken();

				if (token == CLOSING_PAREN) {
					nextToken();
				} else {
					var shouldBreak = false;

					while (!shouldBreak) {
						args.add(parse(-1));

						switch (token) {
							case EOF -> throw new RuntimeException("Syntax error: unmatched parenthesis");
							case ErrorToken error -> throw error.value();
							case COMMA -> nextToken();
							case CLOSING_PAREN -> {
								nextToken();
								shouldBreak = true;
							}
							default -> throw new RuntimeException("Syntax error: Expected comma, got " + token);
						}
					}
				}

				yield new FunctionCallExpression(left, args);
			}
			case OPENING_BRACKET -> {
				nextToken();

				Expression expression = parse(-1);

				if (token == EOF) {
					throw new RuntimeException("Syntax error: unmatched parentheses at " + token);
				}

				if (token == CLOSING_BRACKET) {
					nextToken();
					yield new ArrayAccessExpression(left, expression);
				}

				throw new RuntimeException("Unexpected Token: Expected ], got " + token);
			}
			case IF -> {
				if (lastPrecedence >= BinaryOperationExpression.Operator.CONDITIONAL.precedence) {
					yield left;
				}

				nextToken();
				Expression ifTrue = parse(-1);

				if (token == ELSE) {
					nextToken();

					Expression ifFalse = parse(-1);

					yield new TernaryOperationExpression(left, ifTrue, ifFalse);
				} else {
					yield new BinaryOperationExpression(left, BinaryOperationExpression.Operator.CONDITIONAL, ifTrue);
				}
			}
			default -> {
				BinaryOperationExpression.Operator op = switch (token) {
					case ADD -> BinaryOperationExpression.Operator.ADD;
					case SUBTRACT -> BinaryOperationExpression.Operator.SUBTRACT;
					case MULTIPLY -> BinaryOperationExpression.Operator.MULTIPLY;
					case DIVIDE -> BinaryOperationExpression.Operator.DIVIDE;
					case SET -> BinaryOperationExpression.Operator.SET;
					case CONTEXT_SWITCH -> BinaryOperationExpression.Operator.ARROW;
					case OR -> BinaryOperationExpression.Operator.LOGICAL_OR;
					case AND -> BinaryOperationExpression.Operator.LOGICAL_AND;
					case EQUAL -> BinaryOperationExpression.Operator.EQUAL_TO;
					case NOT_EQUAL -> BinaryOperationExpression.Operator.NOT_EQUAL;
					case LESS -> BinaryOperationExpression.Operator.LESS_THAN;
					case GREATER -> BinaryOperationExpression.Operator.GREATER_THAN;
					case LESS_OR_EQ -> BinaryOperationExpression.Operator.LESS_THAN_OR_EQUAL_TO;
					case GREATER_OR_EQ -> BinaryOperationExpression.Operator.GREATER_THAN_OR_EQUAL_TO;

					default -> null;
				};

				if (op == null) yield left;
				if (lastPrecedence >= op.precedence) yield left;

				nextToken();
				yield new BinaryOperationExpression(left, op, parse(op.precedence));
			}
		};

	}

	public Expression parseSingleExpression() throws IOException {
		var exp = switch (token) {
			case NumberToken(float number) -> new NumberExpression(number);
			case StringToken(String string) -> new StringExpression(string);
			case BREAK -> KeywordExpression.BREAK;
			case CONTINUE -> KeywordExpression.CONTINUE;
			case
				OPENING_PAREN -> { // Must be another expression wrapped in parentheses. If this were a function call, it would have had an identifier on the left, and we would be in parseContinuation
				nextToken();

				Expression expression = parse(-1);

				if (token != CLOSING_PAREN) {
					throw new RuntimeException("Syntax error: unmatched parentheses at " + token);
				}

				nextToken();
				yield expression;
			}
			default -> null;
		};

		if (exp != null) {
			nextToken();
			return exp;
		}

		return switch (token) {
			case RETURN -> {
				nextToken();

				Expression expression = parse(-1);

				yield new UnaryOperationExpression(expression, UnaryOperationExpression.Operator.RETURN);
			}
			case NOT -> {
				nextToken();
				yield new UnaryOperationExpression(parseSingleExpression(), UnaryOperationExpression.Operator.LOGICAL_NEGATE);
			}
			case SUBTRACT -> {
				nextToken();

				yield token instanceof NumberToken(float number) ?
					new NumberExpression(-number) : // May as well optimize this while we are here
					new UnaryOperationExpression(parse(999), UnaryOperationExpression.Operator.NEGATE);
			}
			case IdentifierToken(String first) -> {
				List<String> fields = new ArrayList<>();

				nextToken();

				while (token == DOT) {
					nextToken();

					if (!(token instanceof IdentifierToken(String value))) {
						throw new RuntimeException("Unexpected Token: Expected an identifier after a dot");
					}

					fields.add(value);
					nextToken();
				}

				if (Linker.isVariable(first)) {
					yield new VariableExpression(fields);
				} else {
					yield new AccessExpression(first, fields);
				}
			}
			case OPENING_BRACE -> { // Execution scope, a bit like a lambda
				nextToken();

				List<Expression> expressions = new ArrayList<>();
				while (true) {
					if (token == CLOSING_BRACE) {
						nextToken();
						break;
					}

					expressions.add(parse(-1));

					switch (token) {
						case EOF -> throw new RuntimeException("Syntax error: Unmatched braces");
						case ErrorToken(RuntimeException error) -> throw error;
						case END_EXPRESSION -> nextToken();
						default -> throw new RuntimeException("Expected semicolon, got " + token);
					}
				}

				yield new ComplexExpression(expressions);
			}
			default -> throw new IllegalArgumentException(token.toString());
		};
	}
}
