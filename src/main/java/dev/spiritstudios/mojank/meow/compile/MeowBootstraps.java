package dev.spiritstudios.mojank.meow.compile;

import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.methodDesc;

// TODO: find a better spot to make this public API?
@ApiStatus.Internal
// Used by the compiler
@SuppressWarnings("unused")
public final class MeowBootstraps {
	private static MethodHandles.Lookup getHidden(MethodHandles.Lookup lookup) throws IllegalAccessException {
		return MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, MethodHandles.Lookup.class);
	}

	private static final DynamicLinker dynamicLinker = new DynamicLinkerFactory().createLinker();

	public static final DirectMethodHandleDesc GETTER = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"getter",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class
		)
	);

	public static CallSite getter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor
	) throws NoSuchFieldException, IllegalAccessException {
		final var hiddenLookup = getHidden(lookup);

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

	public static final DirectMethodHandleDesc SETTER = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"setter",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class
		)
	);

	public static CallSite setter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor
	) throws NoSuchFieldException, IllegalAccessException {
		final var hiddenLookup = getHidden(lookup);

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

		final var hiddenLookup = getHidden(lookup);

		final var hiddenClass = hiddenLookup.lookupClass();

		if (!descriptor.returnType().isAssignableFrom(hiddenClass)) {
			throw new IllegalArgumentException("Descriptor expects wrong class: " + descriptor + "; expected: " + hiddenClass);
		}

		return new ConstantCallSite(hiddenLookup.findConstructor(
			hiddenClass,
			descriptor.changeReturnType(void.class)
		).asType(descriptor));
	}

	public static final DirectMethodHandleDesc CONSTRUCTOR_INDEXED = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"constructor",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class,
			int.class
		)
	);

	public static CallSite constructor(
		final MethodHandles.Lookup lookup,
		final String name,
		final MethodType descriptor,
		final int index
	) throws IllegalAccessException, NoSuchMethodException {
		if (!ConstantDescs.DEFAULT_NAME.equals(name)) {
			throw new IllegalArgumentException("Name is not " + ConstantDescs.DEFAULT_NAME + ": " + name);
		}

		final var hiddenLookup =  MethodHandles.classDataAt(lookup, ConstantDescs.DEFAULT_NAME, MethodHandles.Lookup.class, index);

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
