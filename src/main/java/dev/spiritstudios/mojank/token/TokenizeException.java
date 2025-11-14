package dev.spiritstudios.mojank.token;

public class TokenizeException extends RuntimeException {
	public TokenizeException(String message, int line, int col) {
		super(message + " at " + line + ":" + col);
	}
}
