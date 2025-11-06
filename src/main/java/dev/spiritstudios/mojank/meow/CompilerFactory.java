package dev.spiritstudios.mojank.meow;

import org.glavo.classfile.ClassBuilder;
import org.jetbrains.annotations.CheckReturnValue;

import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public sealed abstract class CompilerFactory<T, C extends Compiler<T>> permits MolangFactory {
	protected final MethodHandles.Lookup lookup;
	protected final Class<T> type;

	protected Linker linker = Linker.UNTRUSTED;

	protected CompilerFactory(
		final MethodHandles.Lookup lookup,
		final Class<T> type
	) {
		this.lookup = lookup;
		this.type = type;
	}

	protected CompilerFactory(
		final CompilerFactory<?, ?> builder,
		final Class<T> type
	) {
		this(builder.lookup, type);
	}

	public abstract <N> CompilerFactory<N, ? extends Compiler<N>> withType(final Class<N> type);

	public final CompilerFactory<T, C> withLinker(final Linker linker) {
		this.linker = linker;
		return this;
	}

	@CheckReturnValue
	public abstract Compiler<T> build(ClassBuilder builder);
}
