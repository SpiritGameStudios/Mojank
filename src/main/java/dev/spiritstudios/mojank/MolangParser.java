package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.BreakExpression;
import dev.spiritstudios.mojank.ast.CompoundExpression;
import dev.spiritstudios.mojank.ast.ContinueExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.IdentifierExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.ReturnExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.Util;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MolangParser {
	private static final Logger LOGGER = Util.logger();

	private final MolangLexer lexer;
	private MolangToken token;

	public MolangParser(MolangLexer lexer) throws IOException {
		this.lexer = lexer;
//		this.token = lexer.next();
	}

	private void nextToken() throws IOException {
		token = lexer.next();
		LOGGER.info(token.toString());
	}

	public Expression next() throws IOException {
		nextToken();

		switch (token.kind()) {
			case EOF -> {
				return null;
			}
			case ERROR -> {
				throw new RuntimeException("Invalid token: " + token.value());
			}
			default -> {
				Expression expression = parse(-1);
				if (token.kind() != MolangToken.Kind.EOF && token.kind() != MolangToken.Kind.END_EXPRESSION) {
					throw new RuntimeException("Expected EOF or semicolon, got " + token);
				}

				return expression;
			}
		}
	}

	private Expression parse(int lastPrecedence) throws IOException {
		Expression expression = parseSingleExpression();

		while (true) {
			if (token.kind() == MolangToken.Kind.EOF || token.kind() == MolangToken.Kind.END_EXPRESSION) {
				return expression;
			}

			Expression continuation = parseContinuation(expression, lastPrecedence);

			if (token.kind() == MolangToken.Kind.EOF || token.kind() == MolangToken.Kind.END_EXPRESSION) {
				return continuation;
			}

			if (continuation.equals(expression)) {
				return expression;
			}

			expression = continuation;
		}
	}

	private Expression parseContinuation(Expression left, int lastPrecedence) throws IOException {
		return switch (token.kind()) {
			case EOF, CLOSING_PAREN -> left;
			case OPENING_PAREN -> {
				List<Expression> args = new ArrayList<>(1);
				nextToken();

				if (token.kind() == MolangToken.Kind.CLOSING_PAREN) {
					nextToken();
				} else {
					var shouldBreak = false;

					while (!shouldBreak) {
						args.add(parse(-1));

						switch (token.kind()) {
							case EOF -> throw new RuntimeException("Syntax error: unmatched parenthesis");
							case ERROR -> throw new RuntimeException("Invalid token: " + token.value());
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

				if (token.kind() == MolangToken.Kind.EOF) {
					throw new RuntimeException("Syntax error: unmatched parentheses at " + token);
				}

				if (token.kind() == MolangToken.Kind.CLOSING_BRACKET) {
					nextToken();
					yield new ArrayAccessExpression(left, expression);
				}

				throw new RuntimeException("Unexpected Token: Expected ], got " + token);
			}
			case CONDITIONAL -> {
				if (lastPrecedence >= BinaryOperationExpression.Operator.CONDITIONAL.precedence) {
					yield left;
				}

				nextToken();
				Expression ifTrue = parse(-1);

				if (token.kind() == MolangToken.Kind.ELSE) {
					nextToken();

					Expression ifFalse = parse(-1);

					yield new TernaryOperationExpression(left, ifTrue, ifFalse);
				} else {
					yield new BinaryOperationExpression(left, BinaryOperationExpression.Operator.CONDITIONAL, ifTrue);
				}
			}
			default -> {
				BinaryOperationExpression.Operator op = switch (token.kind()) {
					case ADD -> BinaryOperationExpression.Operator.ADD;
					case SUBTRACT -> BinaryOperationExpression.Operator.SUBTRACT;
					case MULTIPLY -> BinaryOperationExpression.Operator.MULTIPLY;
					case DIVIDE -> BinaryOperationExpression.Operator.DIVIDE;
					case SET -> BinaryOperationExpression.Operator.SET;
					case ARROW -> BinaryOperationExpression.Operator.ARROW;
					case OR -> BinaryOperationExpression.Operator.LOGICAL_OR;
					case AND -> BinaryOperationExpression.Operator.LOGICAL_AND;
					case EQUAL_TO -> BinaryOperationExpression.Operator.EQUAL_TO;
					case NOT_EQUAL -> BinaryOperationExpression.Operator.NOT_EQUAL;
					case LESS_THAN -> BinaryOperationExpression.Operator.LESS_THAN;
					case GREATER_THAN -> BinaryOperationExpression.Operator.GREATER_THAN;
					case LESS_THAN_OR_EQUAL -> BinaryOperationExpression.Operator.LESS_THAN_OR_EQUAL_TO;
					case GREATER_THAN_OR_EQUAL -> BinaryOperationExpression.Operator.GREATER_THAN_OR_EQUAL_TO;

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
		var exp = switch (token.kind()) {
			case NUMBER -> new NumberExpression(Float.parseFloat(token.value()));
			case STRING -> new StringExpression(token.value());
			case TRUE -> NumberExpression.ONE;
			case FALSE -> NumberExpression.ZERO;
			case BREAK -> BreakExpression.INSTANCE;
			case CONTINUE -> ContinueExpression.INSTANCE;
			case OPENING_PAREN -> { // Must be another expression wrapped in parentheses. If this were a function call, it would have had an identifier on the left, and we would be in parseContinuation
				nextToken();

				Expression expression = parse(-1);

				if (token.kind() != MolangToken.Kind.CLOSING_PAREN) {
					throw new RuntimeException("Syntax error: unmatched parentheses at " + token);
				}

				yield expression;
			}
			default -> null;
		};

		if (exp != null) {
			nextToken();
			return exp;
		}

		return switch (token.kind()) {
			case RETURN -> {
				nextToken();

				Expression expression = parse(-1);

				yield new ReturnExpression(expression);
			}
			case NOT -> {
				nextToken();
				yield new UnaryOperationExpression(parseSingleExpression(), UnaryOperationExpression.Operator.LOGICAL_NEGATE);
			}
			case SUBTRACT -> {
				nextToken();

				yield token.kind() == MolangToken.Kind.NUMBER ?
						new NumberExpression(-Float.parseFloat(token.value())) : // May as well optimize this while we are here
						new UnaryOperationExpression(parseSingleExpression(), UnaryOperationExpression.Operator.NEGATE);
			}
			case IDENTIFIER -> {
				Expression expression = new IdentifierExpression(token.value());

				nextToken();

				while (token.kind() == MolangToken.Kind.DOT) {
					nextToken();

					if (token.kind() != MolangToken.Kind.IDENTIFIER) {
						throw new RuntimeException("Unexpected Token: Expected an identifier after a dot");
					}

					expression = new AccessExpression(expression, token.value());
					nextToken();
				}

				yield expression;
			}
			case OPENING_BRACE -> { // Execution scope, a bit like a lambda
				nextToken();

				List<Expression> expressions = new ArrayList<>();
				while (true) {
					if (token.kind() == MolangToken.Kind.CLOSING_BRACE) {
						nextToken();
						break;
					}

					expressions.add(parse(-1));

					if (token.kind() == MolangToken.Kind.EOF) {
						throw new RuntimeException("Syntax error: Unmatched braces");
					} else if (token.kind() == MolangToken.Kind.ERROR) {
						throw new RuntimeException("Invalid token: " + token.value());
					} else if (token.kind() != MolangToken.Kind.END_EXPRESSION) {
						throw new RuntimeException("Expected semicolon, got " + token);
					} else {
						nextToken();
					}
				}

				yield new CompoundExpression(expressions);
			}
			default -> throw new IllegalArgumentException(token.toString());
		};
	}
}
