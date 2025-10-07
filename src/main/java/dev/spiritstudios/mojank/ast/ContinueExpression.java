package dev.spiritstudios.mojank.ast;

public final class ContinueExpression implements Expression {
	public static final ContinueExpression INSTANCE = new ContinueExpression();

	private ContinueExpression() {
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
		return "ContinueExpression[]";
	}
}
