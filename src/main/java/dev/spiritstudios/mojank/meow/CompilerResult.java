package dev.spiritstudios.mojank.meow;

import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;

/**
 * @author Ampflower
 */
@ApiStatus.NonExtendable
public interface CompilerResult<T> {
	ClassDesc DESCRIPTOR = CompilerResult.class.describeConstable().orElseThrow();

	MethodHandle toHandle();

	String toString();

	boolean equals(Object other);

	int hashCode();

	Class<? extends T> getType();
}
