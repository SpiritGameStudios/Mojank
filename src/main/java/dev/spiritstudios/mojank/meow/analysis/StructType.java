package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record StructType(Map<String, Type> members) implements Type {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Struct[").pushIndent();

		members.forEach((name, type) -> {
			builder.newline().append("'").append(name).append("'").append(" -> ");
			type.append(builder);
		});

		builder.popIndent().newline().append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
