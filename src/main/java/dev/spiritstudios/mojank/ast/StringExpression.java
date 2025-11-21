package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.meow.link.Linker;
import org.jetbrains.annotations.NotNull;

public record StringExpression(String value) implements Expression {
	@Override
	public @NotNull String toString() {
		return "String(\"" + value + "\")";
	}

	@Override
	public boolean constant(Linker linker) {
		return true;
	}
}
