package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record VariableExpression(List<String> fields) implements Expression {
	public VariableExpression(String... fields) {
		this(List.of(fields));
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Access[");

		for (int i = 0; i < fields.size(); i++) {
			String field = fields.get(i);
			builder.append("\"").append(field).append("\"");

			if (i != fields.size() - 1) {
				builder.append(", ");
			}
		}

		builder.append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
