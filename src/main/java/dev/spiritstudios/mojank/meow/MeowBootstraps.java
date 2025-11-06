package dev.spiritstudios.mojank.meow;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MeowBootstraps {
	public static CallSite getter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor,
		Class<?> hiddenClass
	) throws NoSuchFieldException, IllegalAccessException {
		var hiddenLookup = lookup.in(hiddenClass);

		return new ConstantCallSite(hiddenLookup.findGetter(
			hiddenClass,
			field,
			descriptor.returnType()
		).asType(descriptor));
	}

	public static CallSite setter(
		MethodHandles.Lookup lookup,
		String field,
		MethodType descriptor,
		Class<?> hiddenClass
	) throws NoSuchFieldException, IllegalAccessException {
		var hiddenLookup = lookup.in(hiddenClass);

		return new ConstantCallSite(hiddenLookup.findSetter(
			hiddenClass,
			field,
			descriptor.parameterType(1)
		).asType(descriptor));
	}
}
