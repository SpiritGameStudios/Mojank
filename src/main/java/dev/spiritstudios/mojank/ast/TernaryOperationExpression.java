package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.link.Linker;
import org.jetbrains.annotations.NotNull;

public record TernaryOperationExpression(Expression condition, Expression ifTrue, Expression ifFalse) implements Expression {
	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Ternary[").pushIndent().newline();
		condition.append(builder);
		builder.append(",").newline();
		builder.append("ifTrue = ");
		ifTrue.append(builder);
		builder.append(",").newline();
		builder.append("ifFalse = ");
		ifFalse.append(builder);
		builder.popIndent().newline().append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}

	@Override
	public boolean constant(Linker linker) {
		return condition.constant(linker) && ifTrue.constant(linker) && ifFalse().constant(linker);
	}
}
