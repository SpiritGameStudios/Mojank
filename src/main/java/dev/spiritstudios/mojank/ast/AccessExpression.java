package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AccessExpression(String first, List<String> fields) implements Expression {
	public AccessExpression(String first, String... fields) {
		this(first, List.of(fields));
	}

	public static AccessExpression variable(String... fields) {
		return new AccessExpression("variable", fields);
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Access[");
		builder.append("\"").append(first).append("\", ");

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
