package dev.spiritstudios.mojank.meow;

import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public final class MolangBuilder<T> extends CompilerBuilder<T, MolangCompiler<T>> {
	public MolangBuilder(
		final MethodHandles.Lookup lookup,
		final Class<T> type
	) {
		super(lookup, type);
	}

	@Override
	public MolangCompiler<T> build() {
		return new MolangCompiler<>(lookup, type, linker);
	}
}
