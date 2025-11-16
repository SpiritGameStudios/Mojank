package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.compile.Linker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ComplexExpression(List<Expression> expressions) implements Expression {
	@Override
	public boolean constant(Linker linker) {
		return false;
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("ComplexExpression {").pushIndent();
		expressions.forEach(exp -> {
			builder.newline();
			exp.append(builder);
		});
		builder.popIndent().newline().append("}");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
