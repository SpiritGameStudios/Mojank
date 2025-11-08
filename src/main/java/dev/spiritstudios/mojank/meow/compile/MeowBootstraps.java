package dev.spiritstudios.mojank.meow.compile;

import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// TODO: find a better spot to make this public API?
@ApiStatus.Internal
// Used by the compiler
@SuppressWarnings("unused")
public final class MeowBootstraps {
	private static MethodHandles.Lookup getDeferred(MethodHandles.Lookup lookup) throws IllegalAccessException {
		return (MethodHandles.Lookup) MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Deferred.class).get();
	}

	public static CallSite getter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor
	) throws NoSuchFieldException, IllegalAccessException {
		final var hiddenLookup = getDeferred(lookup);

		if (descriptor.parameterCount() != 1) {
			throw new IllegalArgumentException("Descriptor length incorrect: " + descriptor);
		}

		if (descriptor.returnType() == void.class) {
			throw new IllegalArgumentException("Descriptor doesn't return: " + descriptor);
		}

		final var hiddenClass = hiddenLookup.lookupClass();

		if (!descriptor.parameterType(0).isAssignableFrom(hiddenClass)) {
			throw new IllegalArgumentException("Descriptor expects wrong class: " + descriptor + "; expected: " + hiddenClass);
		}

		return new ConstantCallSite(hiddenLookup.findGetter(
			hiddenClass,
			field,
			descriptor.returnType()
		).asType(descriptor));
	}

	public static CallSite setter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor
	) throws NoSuchFieldException, IllegalAccessException {
		final var hiddenLookup = getDeferred(lookup);

		if (descriptor.parameterCount() != 2) {
			throw new IllegalArgumentException("Descriptor length incorrect: " + descriptor);
		}

		if (descriptor.returnType() != void.class) {
			throw new IllegalArgumentException("Descriptor returns: " + descriptor);
		}

		final var hiddenClass = hiddenLookup.lookupClass();

		if (!descriptor.parameterType(0).isAssignableFrom(hiddenClass)) {
			throw new IllegalArgumentException("Descriptor expects wrong class: " + descriptor + "; expected: " + hiddenClass);
		}

		return new ConstantCallSite(hiddenLookup.findSetter(
			hiddenClass,
			field,
			descriptor.parameterType(1)
		).asType(descriptor));
	}

	public static CallSite constructor(
		final MethodHandles.Lookup lookup,
		final String name,
		final MethodType descriptor
	) throws IllegalAccessException, NoSuchMethodException {
		if (!ConstantDescs.DEFAULT_NAME.equals(name)) {
			throw new IllegalArgumentException("Name is not " + ConstantDescs.DEFAULT_NAME + ": " + name);
		}

		final var hiddenLookup = getDeferred(lookup);

		final var hiddenClass = hiddenLookup.lookupClass();

		if (!descriptor.returnType().isAssignableFrom(hiddenClass)) {
			throw new IllegalArgumentException("Descriptor expects wrong class: " + descriptor + "; expected: " + hiddenClass);
		}

		return new ConstantCallSite(hiddenLookup.findConstructor(
			hiddenClass,
			descriptor.changeReturnType(void.class)
		).asType(descriptor));
	}
}
