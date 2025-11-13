package dev.spiritstudios.mojank.meow.compile;

import org.jetbrains.annotations.ApiStatus;

import java.lang.invoke.MethodHandle;

/**
 * @author Ampflower
 */
@ApiStatus.NonExtendable
public interface CompilerResult<T> {
	MethodHandle toHandle();

	String toString();

	boolean equals(Object other);

	int hashCode();

	Class<? extends T> getType();

	// TODO: strictly genericise this, somehow?
//	Variables createVariables();
}
