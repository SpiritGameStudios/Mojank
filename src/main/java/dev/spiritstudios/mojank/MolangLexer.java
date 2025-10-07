package dev.spiritstudios.mojank;

import java.io.IOException;
import java.io.Reader;

import it.unimi.dsi.fastutil.ints.IntSet;

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

	private int index = 0;
	private int line = 1;
	private int col = 1;

	private int nextCharacter;

	public MolangLexer(Reader reader) throws IOException {
		this.reader = reader;
		this.nextCharacter = reader.read();
	}

	public MolangToken next() throws IOException {
		int codepoint = nextCharacter;

		while (SKIP.contains(codepoint)) {
			codepoint = readChar();
		}

		if (codepoint == -1) {
			return new MolangToken(MolangToken.Kind.EOF, index, index + 1);
		}

		int start = index;
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

			return new MolangToken(MolangToken.Kind.NUMBER, number.toString(), start, index);
		} else if (isValidIdentifierStart(codepoint)) { // [A-z_]
			StringBuilder builder = new StringBuilder();
			builder.appendCodePoint(codepoint);

			while (isValidIdentifier(codepoint = readChar())) {
				builder.appendCodePoint(codepoint);
			}

			String identifier = builder.toString();

			return switch (identifier) {
				case "return" -> new MolangToken(MolangToken.Kind.RETURN, start, index);
				case "break" -> new MolangToken(MolangToken.Kind.BREAK, start, index);
				case "continue" -> new MolangToken(MolangToken.Kind.CONTINUE, start, index);
				case "true" -> new MolangToken(MolangToken.Kind.TRUE, start, index);
				case "false" -> new MolangToken(MolangToken.Kind.FALSE, start, index);
				default -> new MolangToken(MolangToken.Kind.IDENTIFIER, identifier, start, index);
			};
		} else if (codepoint == '\'') {
			StringBuilder builder = new StringBuilder();

			codepoint = readChar();
			while (codepoint != '\'') {
				builder.appendCodePoint(codepoint);

				codepoint = readChar();

				if (codepoint == -1) {
					return new MolangToken(MolangToken.Kind.ERROR, "Found unclosed string at line " + line + " column" + col, start, index);
				}
			}

			readChar();

			return new MolangToken(MolangToken.Kind.STRING, builder.toString(), start, index);
		} else {
			var token = switch (codepoint) {
				case '!' -> {
					if (readChar() == '=') {
						yield new MolangToken(MolangToken.Kind.NOT_EQUAL, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.NOT, start, index);
					}
				}
				case '|' -> {
					if (readChar() == '|') {
						yield new MolangToken(MolangToken.Kind.OR, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.ERROR, "Binary operations are not supported.", start, index);
					}
				}
				case '&' -> {
					if (readChar() == '&') {
						yield new MolangToken(MolangToken.Kind.AND, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.ERROR, "Binary operations are not supported.", start, index);
					}
				}
				case '<' -> {
					if (readChar() == '=') {
						yield new MolangToken(MolangToken.Kind.LESS_THAN_OR_EQUAL, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.LESS_THAN, start, index);
					}
				}
				case '>' -> {
					if (readChar() == '=') {
						yield new MolangToken(MolangToken.Kind.GREATER_THAN_OR_EQUAL, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.GREATER_THAN, start, index);
					}
				}
				case '?' -> {
					if (readChar() == '?') {
						yield new MolangToken(MolangToken.Kind.NULL_COALESCE, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.CONDITIONAL, start, index);
					}
				}
				case '=' -> {
					if (readChar() == '=') {
						yield new MolangToken(MolangToken.Kind.EQUAL_TO, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.SET, start, index);
					}
				}
				case '*' -> new MolangToken(MolangToken.Kind.MULTIPLY, start, index);
				case '/' -> new MolangToken(MolangToken.Kind.DIVIDE, start, index);
				case '+' -> new MolangToken(MolangToken.Kind.ADD, start, index);
				case '-' -> {
					if (readChar() == '>') {
						yield new MolangToken(MolangToken.Kind.ARROW, start, index);
					} else {
						yield new MolangToken(MolangToken.Kind.SUBTRACT, start, index);
					}
				}
				case '(' -> new MolangToken(MolangToken.Kind.OPENING_PAREN, start, index);
				case ')' -> new MolangToken(MolangToken.Kind.CLOSING_PAREN, start, index);
				case '{' -> new MolangToken(MolangToken.Kind.OPENING_BRACE, start, index);
				case '}' -> new MolangToken(MolangToken.Kind.CLOSING_BRACE, start, index);
				case '[' -> new MolangToken(MolangToken.Kind.OPENING_BRACKET, start, index);
				case ']' -> new MolangToken(MolangToken.Kind.CLOSING_BRACKET, start, index);
				case '.' -> new MolangToken(MolangToken.Kind.DOT, start, index);
				case ';' -> new MolangToken(MolangToken.Kind.END_EXPRESSION, start, index);
				case ',' -> new MolangToken(MolangToken.Kind.COMMA, start, index);
				case ':' -> new MolangToken(MolangToken.Kind.ELSE, start, index);
				default -> new MolangToken(MolangToken.Kind.ERROR, "Unexpected token", start, index);
			};

			readChar();
			return token;
		}
	}

	private int readChar() throws IOException {
		int character = reader.read();
		index++;
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
