package dev.spiritstudios.mojank.ast;

public class IndentedStringBuilder {
	private final StringBuilder backing;
	private int indent = 0;

	public IndentedStringBuilder(StringBuilder backing) {
		this.backing = backing;
	}

	public IndentedStringBuilder append(String string) {
		backing.append(string);
		return this;
	}

	public IndentedStringBuilder newline() {
		backing.append('\n').append("\t".repeat(indent));
		return this;
	}

	public IndentedStringBuilder pushIndent() {
		indent++;
		return this;
	}

	public IndentedStringBuilder popIndent() {
		indent--;
		return this;
	}

	@Override
	public String toString() {
		return backing.toString();
	}
}
