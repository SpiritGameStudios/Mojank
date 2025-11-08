package dev.spiritstudios.mojank.token;

public record ErrorToken(RuntimeException value) implements MolangToken {
	public ErrorToken(String message) {
		this(new ParseException(message));
	}
}
