package dev.spiritstudios.mojank;

import dev.spiritstudios.mojank.token.ErrorToken;
import dev.spiritstudios.mojank.token.IdentifierToken;
import dev.spiritstudios.mojank.token.MolangToken;
import dev.spiritstudios.mojank.token.NumberToken;
import dev.spiritstudios.mojank.token.StringToken;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.spiritstudios.mojank.token.OperatorToken.ADD;
import static dev.spiritstudios.mojank.token.OperatorToken.AND;
import static dev.spiritstudios.mojank.token.OperatorToken.BREAK;
import static dev.spiritstudios.mojank.token.OperatorToken.CLOSING_BRACE;
import static dev.spiritstudios.mojank.token.OperatorToken.CLOSING_BRACKET;
import static dev.spiritstudios.mojank.token.OperatorToken.CLOSING_PAREN;
import static dev.spiritstudios.mojank.token.OperatorToken.COMMA;
import static dev.spiritstudios.mojank.token.OperatorToken.CONTEXT_SWITCH;
import static dev.spiritstudios.mojank.token.OperatorToken.CONTINUE;
import static dev.spiritstudios.mojank.token.OperatorToken.DIVIDE;
import static dev.spiritstudios.mojank.token.OperatorToken.DOT;
import static dev.spiritstudios.mojank.token.OperatorToken.ELSE;
import static dev.spiritstudios.mojank.token.OperatorToken.END_EXPRESSION;
import static dev.spiritstudios.mojank.token.OperatorToken.EOF;
import static dev.spiritstudios.mojank.token.OperatorToken.EQUAL;
import static dev.spiritstudios.mojank.token.OperatorToken.GREATER;
import static dev.spiritstudios.mojank.token.OperatorToken.GREATER_OR_EQ;
import static dev.spiritstudios.mojank.token.OperatorToken.IF;
import static dev.spiritstudios.mojank.token.OperatorToken.LESS;
import static dev.spiritstudios.mojank.token.OperatorToken.LESS_OR_EQ;
import static dev.spiritstudios.mojank.token.OperatorToken.MULTIPLY;
import static dev.spiritstudios.mojank.token.OperatorToken.NOT;
import static dev.spiritstudios.mojank.token.OperatorToken.NOT_EQUAL;
import static dev.spiritstudios.mojank.token.OperatorToken.NULL_COALESCE;
import static dev.spiritstudios.mojank.token.OperatorToken.OPENING_BRACE;
import static dev.spiritstudios.mojank.token.OperatorToken.OPENING_BRACKET;
import static dev.spiritstudios.mojank.token.OperatorToken.OPENING_PAREN;
import static dev.spiritstudios.mojank.token.OperatorToken.OR;
import static dev.spiritstudios.mojank.token.OperatorToken.RETURN;
import static dev.spiritstudios.mojank.token.OperatorToken.SET;
import static dev.spiritstudios.mojank.token.OperatorToken.SUBTRACT;

public class MolangLexer {
	private static final IntSet SKIP = IntSet.of(
			' ',
			'\t',
			'\n',
			'\r'
	);

	private static boolean isValidIdentifierStart(int codepoint) {
		return ('a' <= codepoint && codepoint <= 'z') || ('A' <= codepoint && codepoint <= 'Z') || codepoint == '_';
	}

	private static boolean isValidIdentifier(int codepoint) {
		return isValidIdentifierStart(codepoint) || Character.isDigit(codepoint);
	}


	private final Reader reader;

	private int line = 1;
	private int col = 1;

	private int nextCharacter;

	public MolangLexer(Reader reader) throws IOException {
		this.reader = reader;
		this.nextCharacter = reader.read();
	}

	private float parseNumber(String string) {
		// FIXME: im fairly sure this will accept some things that may be invalid in official molang.
		return Float.parseFloat(string);
	}

	public MolangToken next() throws IOException {
		int codepoint = nextCharacter;

		while (SKIP.contains(codepoint)) {
			codepoint = readChar();
		}

		if (codepoint == -1) {
			return EOF;
		}

		if (Character.isDigit(codepoint)) {
			StringBuilder number = new StringBuilder();

			while (Character.isDigit(codepoint)) {
				number.appendCodePoint(codepoint);

				codepoint = readChar();
			}

			if (codepoint == '.') {
				codepoint = readChar();
				number.append('.');

				while (Character.isDigit(codepoint)) {
					number.appendCodePoint(codepoint);

					codepoint = readChar();
				}
			}

			return new NumberToken(parseNumber(number.toString()));
		} else if (isValidIdentifierStart(codepoint)) { // [A-z_]
			StringBuilder builder = new StringBuilder();

			do {
				builder.appendCodePoint(codepoint);
			} while (isValidIdentifier(codepoint = readChar()));

			String identifier = builder.toString().toLowerCase(Locale.ROOT);

			return switch (identifier) {
				case "return" -> RETURN;
				case "break" -> BREAK;
				case "continue" -> CONTINUE;
				case "true" -> NumberToken.ONE;
				case "false" -> NumberToken.ZERO;
				default -> new IdentifierToken(identifier);
			};
		} else if (codepoint == '\'') {
			StringBuilder builder = new StringBuilder();

			codepoint = readChar();
			while (codepoint != '\'') {
				builder.appendCodePoint(codepoint);

				codepoint = readChar();

				if (codepoint == -1) {
					return new ErrorToken("Found unclosed string", line, col);
				}
			}

			readChar();

			return new StringToken(builder.toString());
		} else {
			var token = switch (codepoint) {
				case '!' -> {
					if (readChar() == '=') {
						readChar();
						yield NOT_EQUAL;
					} else {
						yield NOT;
					}
				}
				case '|' -> {
					if (readChar() == '|') {
						readChar();
						yield OR;
					} else {
						yield new ErrorToken("Binary operations are not supported! Found use of binary OR", line, col);
					}
				}
				case '&' -> {
					if (readChar() == '&') {
						readChar();
						yield AND;
					} else {
						yield new ErrorToken("Binary operations are not supported! Found use of binary AND", line, col);
					}
				}
				case '<' -> {
					if (readChar() == '=') {
						readChar();
						yield LESS_OR_EQ;
					} else {
						yield LESS;
					}
				}
				case '>' -> {
					if (readChar() == '=') {
						readChar();
						yield GREATER_OR_EQ;
					} else {
						yield GREATER;
					}
				}
				case '?' -> {
					if (readChar() == '?') {
						readChar();
						yield NULL_COALESCE;
					} else {
						yield IF;
					}
				}
				case '=' -> {
					if (readChar() == '=') {
						readChar();
						yield EQUAL;
					} else {
						yield SET;
					}
				}

				case '-' -> {
					if (readChar() == '>') {
						readChar();
						yield CONTEXT_SWITCH;
					} else {
						yield SUBTRACT;
					}
				}
				default -> null;
			};

			if (token != null) {
				return token;
			}

			token = switch (codepoint) {
				case '*' -> MULTIPLY;
				case '/' -> DIVIDE;
				case '+' -> ADD;
				case '(' -> OPENING_PAREN;
				case ')' -> CLOSING_PAREN;
				case '{' -> OPENING_BRACE;
				case '}' -> CLOSING_BRACE;
				case '[' -> OPENING_BRACKET;
				case ']' -> CLOSING_BRACKET;
				case '.' -> DOT;
				case ';' -> END_EXPRESSION;
				case ',' -> COMMA;
				case ':' -> ELSE;
				case '"' -> new ErrorToken("Unexpected token \", Did you mean ' ?", line, col);
				default -> new ErrorToken("Unexpected token '" + Character.getName(codepoint) + "'", line, col);
			};

			readChar();
			return token;
		}
	}

	public List<MolangToken> readAll() throws IOException {
		var result = new ArrayList<MolangToken>();

		while (true) {
			var token = next();
			result.add(token);
			if (token == EOF) break;
		}

		return result;
	}

	private int readChar() throws IOException {
		int character = reader.read();
		if (character == '\n') {
			line++;
			col = 1;
		} else {
			col++;
		}

		nextCharacter = character;

		return character;
	}
}
