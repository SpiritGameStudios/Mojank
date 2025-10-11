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

	private MolangBuilder(
		final CompilerBuilder<?, ?> builder,
		final Class<T> type
	) {
		super(builder, type);
	}

	@Override
	public <N> MolangBuilder<N> withType(final Class<N> type) {
		return new MolangBuilder<>(this, type);
	}

	@Override
	public MolangCompiler<T> build() {
		return new MolangCompiler<>(lookup, type, linker);
	}
}
