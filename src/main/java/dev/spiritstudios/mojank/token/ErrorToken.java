package dev.spiritstudios.mojank.token;

public record ErrorToken(RuntimeException value) implements MolangToken {
	public ErrorToken(String message, int line, int col) {
		this(new TokenizeException(message, line, col));
	}
}
