package dev.spiritstudios.mojank.meow;

import org.jetbrains.annotations.ApiStatus;

import java.lang.invoke.MethodHandle;

/**
 * @author Ampflower
 */
@ApiStatus.NonExtendable
public interface CompilerResult<T> {
	Compiler<T> getCompiler();

	MethodHandle toHandle();

	String toString();

	boolean equals(Object other);

	int hashCode();

	Class<? extends T> getType();

	@SuppressWarnings("unchecked")
	default T get() {
		return (T) this;
	}
}
