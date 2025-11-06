package dev.spiritstudios.mojank.meow;

import org.glavo.classfile.ClassBuilder;

import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public final class MolangFactory<T> extends CompilerFactory<T, MolangCompiler<T>> {
	public MolangFactory(
		final MethodHandles.Lookup lookup,
		final Class<T> type
	) {
		super(lookup, type);
	}

	private MolangFactory(
		final CompilerFactory<?, ?> builder,
		final Class<T> type
	) {
		super(builder, type);
	}

	@Override
	public <N> MolangFactory<N> withType(final Class<N> type) {
		return new MolangFactory<>(this, type);
	}

	@Override
	public Compiler<T> build(ClassBuilder builder) {
		return new MolangCompiler<>(lookup, type, linker, builder);
	}
}
