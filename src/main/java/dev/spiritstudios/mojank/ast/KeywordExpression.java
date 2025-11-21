package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.meow.link.Linker;

public enum KeywordExpression implements Expression {
	BREAK,
	CONTINUE;

	@Override
	public boolean constant(Linker linker) {
		return false;
	}
}
