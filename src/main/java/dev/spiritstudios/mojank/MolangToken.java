package dev.spiritstudios.mojank;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

public record MolangToken(Kind kind, @UnknownNullability String value, int start, int end) {
	public MolangToken(Kind kind, int start, int end) {
		this(kind, null, start, end);
	}

	public enum Kind {
		EOF,
		IDENTIFIER,

		RETURN,
		BREAK,
		CONTINUE,
		TRUE,
		FALSE,

		STRING,
		NUMBER,

		LESS_THAN,
		LESS_THAN_OR_EQUAL,

		GREATER_THAN,
		GREATER_THAN_OR_EQUAL,

		NOT_EQUAL,

		EQUAL_TO,

		NOT,
		OR,
		AND,

		CONDITIONAL,
		ELSE,
		NULL_COALESCE,

		ARROW,

		DOT,

		SET,
		END_EXPRESSION,
		COMMA,

		MULTIPLY,
		DIVIDE,
		ADD,
		SUBTRACT,

		OPENING_PAREN,
		CLOSING_PAREN,

		OPENING_BRACE,
		CLOSING_BRACE,

		OPENING_BRACKET,
		CLOSING_BRACKET,

		ERROR
	}

	@Override
	public @NotNull String toString() {
		var builder = new StringBuilder();
		builder.append(kind.toString());
		builder.append('(');
		if (value != null) builder.append(value).append(", ");
		builder.append(start).append("..").append(end);
		builder.append(')');

		return builder.toString();
	}
}
