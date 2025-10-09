package dev.spiritstudios.mojank.meow;

import org.jetbrains.annotations.CheckReturnValue;

import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public sealed abstract class CompilerBuilder<T, C extends Compiler<T>> permits MolangBuilder {
	protected final MethodHandles.Lookup lookup;
	protected final Class<T> type;

	protected Linker linker = Linker.untrusted;

	CompilerBuilder(
		final MethodHandles.Lookup lookup,
		final Class<T> type
	) {
		this.lookup = lookup;
		this.type = type;
	}

	public final CompilerBuilder<T, C> withLinker(final Linker linker) {
		this.linker = linker;
		return this;
	}

	@CheckReturnValue
	public abstract C build();
}
