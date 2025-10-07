package dev.spiritstudios.mojank.ast;

public final class BreakExpression implements Expression {
	public static final BreakExpression INSTANCE = new BreakExpression();

	private BreakExpression() {
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj != null && obj.getClass() == this.getClass();
	}

	@Override
	public int hashCode() {
		return 1;
	}

	@Override
	public String toString() {
		return "BreakExpression[]";
	}
}
