package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.meow.Variables;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;
import java.util.Objects;

import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.desc;
import static dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator.methodDesc;
import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.DEFAULT_NAME;

// TODO: find a better spot to make this public API?
@ApiStatus.Internal
// Used by the compiler
@SuppressWarnings("unused")
public final class MeowBootstraps {
	private static MethodHandles.Lookup getHidden(MethodHandles.Lookup lookup) throws IllegalAccessException {
		return MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, MethodHandles.Lookup.class);
	}

	private static final DynamicLinker dynamicLinker = new DynamicLinkerFactory().createLinker();

	public static final MethodHandle ZERO = MethodHandles.zero(float.class);

	public static final MethodHandle META_GETTER, META_SETTER;

	private static final ClassDesc SELF = desc(MeowBootstraps.class);

	static {
		try {
			final var lookup = MethodHandles.lookup();

			META_GETTER = lookup.findVirtual(
				Variables.class,
				"getGetter",
				MethodType.methodType(MethodHandle.class, String.class)
			);
			META_SETTER = lookup.findVirtual(
				Variables.class,
				"getSetter",
				MethodType.methodType(MethodHandle.class, String.class)
			);
		} catch (ReflectiveOperationException e) {
			// Something has gone horribly wrong if we reach here.
			throw new AssertionError(e);
		}
	}

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
		SELF,
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


		final var a = MethodHandles.invoker(descriptor.dropParameterTypes(0, 1));
		final var b = MethodHandles.insertArguments(META_GETTER, 1, field);

		return new ConstantCallSite(
			MethodHandles.filterArguments(a, 0, b).asType(descriptor)
		);
		/*
		return new ConstantCallSite(hiddenLookup.findGetter(
			hiddenClass,
			field,
			descriptor.returnType()
		).asType(descriptor));
		*/
	}

	public static final DirectMethodHandleDesc SET = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		SELF,
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

		final var a = MethodHandles.invoker(descriptor.dropParameterTypes(0, 1));
		final var b = MethodHandles.insertArguments(META_SETTER, 1, field);

		return new ConstantCallSite(
			MethodHandles.filterArguments(a, 0, b)
		);
		/*
		return new ConstantCallSite(hiddenLookup.findSetter(
			hiddenClass,
			field,
			descriptor.parameterType(1)
		).asType(descriptor));
		*/
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
		SELF,
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
		validateDefaultName(name);

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

	public static DynamicCallSiteDesc constructOfIndexed(final int index) {
		return DynamicCallSiteDesc.of(
			MeowBootstraps.CONSTRUCT_INDEXED,
			DEFAULT_NAME,
			methodDesc(Variables.class),
			index
		);
	}

	public static final DirectMethodHandleDesc CALL_SITE_OF = MethodHandleDesc.ofMethod(
		DirectMethodHandleDesc.Kind.STATIC,
		SELF,
		"callSiteOf",
		MethodTypeDesc.of(
			CD_CallSite,
			CD_MethodHandles_Lookup,
			CD_String,
			desc(TypeDescriptor.class),
			CD_MethodHandle
		)
	);

	/**
	 * Condy bootstrap that turns a given {@link MethodHandle} into a {@link CallSite}.
	 * <p>
	 * indy can also use this but, please just call the method directly...
	 */
	public static CallSite callSiteOf(
		final MethodHandles.Lookup lookup,
		final String name,
		final TypeDescriptor descriptor,
		MethodHandle handle
	) {
		Objects.requireNonNull(lookup, "lookup");
		Objects.requireNonNull(handle, "handle");
		validateDefaultName(name);
		switch (descriptor) {
			case Class<?> clazz -> validateClass(CallSite.class, clazz);
			case MethodType methodType -> {
				validateSignature(methodType, handle.type());
				handle = handle.asType(methodType);
			}
			case null, default -> throw new IllegalArgumentException("Unknown descriptor: " + descriptor);
		}
		return new ConstantCallSite(handle);
	}

	private static void validateDefaultName(final String name) {
		if (!ConstantDescs.DEFAULT_NAME.equals(name)) {
			throw new IllegalArgumentException("Name is not " + ConstantDescs.DEFAULT_NAME + ": " + name);
		}
	}

	private static void validateClass(final Class<?> expected, final Class<?> actual) {
		if (expected != actual) {
			throw new IllegalArgumentException("expected: " + expected + ", got: " + actual);
		}
	}

	private static void validateSignature(final MethodType expected, final MethodType actual) {
		if (expected.equals(actual)) {
			return;
		}

		if (expected.parameterCount() != actual.parameterCount()) {
			throw new IllegalArgumentException("Parameter mismatch: expected: " + expected + ", got: " + actual);
		}

		if (!expected.returnType().isAssignableFrom(actual.returnType())) {
			throw new IllegalArgumentException(
				"Return mismatch:\n\texpected: " + expected.returnType() + ", got: " + actual.returnType() + "\n\t" + expected + ", got: " + actual
			);
		}

		for (int i = 0; i < expected.parameterCount(); i++) {
			final var expectedParam = expected.parameterType(i);
			final var actualParam = actual.parameterType(i);
			if (expectedParam.isAssignableFrom(actualParam)) {
				continue;
			}
			throw new IllegalArgumentException(
				"Parameter mismatch @ " + (i + 1) + ":\n\texpected: " + expectedParam + ", got: " + actualParam + "\n\t" + expected + ", got: " + actual
			);
		}
	}
}
