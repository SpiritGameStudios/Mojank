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


//	public static CallSite get(
//		MethodHandles.Lookup lookup,
//		String name,
//		MethodType type,
//		VariableExpression variable,
//		StructType variablesType
//	) throws IllegalAccessException, NoSuchFieldException {
//		var variableLookup = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, MethodHandles.Lookup.class);
//
//		for (String field : variable.fields()) {
//			variableLookup.(
//				variableLookup.lookupClass(),
//				field,
//				Variables.class
//			);
//		}
//	}

	public static final DirectMethodHandleDesc GET = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"get",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class
		)
	);

	public static CallSite get(
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

	public static final DirectMethodHandleDesc SET = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"set",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class
		)
	);

	public static CallSite set(
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

	public static CallSite construct(
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

	public static final DirectMethodHandleDesc CONSTRUCT_INDEXED = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		desc(MeowBootstraps.class),
		"construct",
		methodDesc(
			CallSite.class,
			MethodHandles.Lookup.class,
			String.class,
			MethodType.class,
			int.class
		)
	);

	public static CallSite construct(
		final MethodHandles.Lookup lookup,
		final String name,
		final MethodType descriptor,
		final int index
	) throws IllegalAccessException, NoSuchMethodException {
		if (!ConstantDescs.DEFAULT_NAME.equals(name)) {
			throw new IllegalArgumentException("Name is not " + ConstantDescs.DEFAULT_NAME + ": " + name);
		}

		final var hiddenLookup = MethodHandles.classDataAt(lookup, ConstantDescs.DEFAULT_NAME, MethodHandles.Lookup.class, index);

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
