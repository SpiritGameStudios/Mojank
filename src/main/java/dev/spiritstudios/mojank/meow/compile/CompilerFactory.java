package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.meow.Parser;
import org.jetbrains.annotations.CheckReturnValue;

import java.lang.invoke.MethodHandles;

/**
 * @author Ampflower
 **/
public final class CompilerFactory<T> {
	private final MethodHandles.Lookup lookup;
	private final Class<T> type;

	private Linker linker = Linker.UNTRUSTED;

	private Parser parser = Parser.MOLANG;

	public CompilerFactory(
		final MethodHandles.Lookup lookup,
		final Class<T> type
	) {
		this.lookup = lookup;
		this.type = type;
	}

	public <N> CompilerFactory<N> withType(final Class<N> type) {
		return new CompilerFactory<N>(lookup, type);
	}

	public CompilerFactory<T> withLinker(final Linker linker) {
		this.linker = linker;
		return this;
	}

	public CompilerFactory<T> withParser(final Parser parser) {
		this.parser = parser;
		return this;
	}

	@CheckReturnValue
	public Compiler<T> build() {
		return new Compiler<>(
			lookup,
			type,
			linker,
			parser
		);
	}
}
