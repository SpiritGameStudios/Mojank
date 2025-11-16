package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.compile.Linker;
import org.jetbrains.annotations.NotNull;

public record UnaryOperationExpression(Expression value, Operator operator) implements Expression {
	public enum	Operator {
		NEGATE,
		LOGICAL_NEGATE,
		RETURN
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("UnaryOperation[").append(operator.toString()).append(", ");
		value.append(builder);
		builder.append("]");
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}

	public static UnaryOperationExpression return_(Expression exp) {
		return new UnaryOperationExpression(exp, Operator.RETURN);
	}

	@Override
	public boolean constant(Linker linker) {
		return value.constant(linker) && operator != Operator.RETURN;
	}
}
