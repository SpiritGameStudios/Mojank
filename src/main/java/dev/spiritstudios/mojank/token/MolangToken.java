package dev.spiritstudios.mojank.token;

public sealed interface MolangToken permits ErrorToken, IdentifierToken,
	NumberToken, OperatorToken, StringToken {

}
