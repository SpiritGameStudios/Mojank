package dev.spiritstudios.mojank.compile;

import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.compile.link.Alias;
import dev.spiritstudios.mojank.compile.link.Linker;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static dev.spiritstudios.mojank.compile.BoilerplateGenerator.*;
import static dev.spiritstudios.mojank.compile.Descriptors.desc;
import static dev.spiritstudios.mojank.compile.Descriptors.methodDesc;

public class Compiler {
	public static byte[] compileToBytecode(
		MethodHandles.Lookup lookup,
		Linker linker,
		Class<?> targetClass,
		Expression expression,
		String source
	) throws Throwable {
		Method targetMethod = linker.tryFunctionalClass(targetClass)
			.orElseThrow(() -> new IllegalArgumentException("'" + targetClass + "' is not a valid functional interface"));

		CompileContext context = new CompileContext(
			linker,
			targetMethod
		);

		var desc = ClassDesc.of(
			lookup.lookupClass().getPackage().getName(),
			"\uD83C\uDFF3️\u200D⚧️️" + targetClass.getSimpleName()
		);

		return ClassFile.of()
			.build(
				desc,
				cb -> {
					writeCompilerResultStub(desc, targetClass, targetMethod, source, cb);

					cb.withMethod(
						targetMethod.getName(),
						methodDesc(targetMethod.getReturnType(), targetMethod.getParameterTypes()),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
						mb -> mb.withCode(cob -> {
							// Fill in the LVT for the parametersByName based on the aliases since you can't reflectively access the names in non-ancient JVMs
							var params = targetMethod.getParameters();
							for (int i = 0; i < params.length; i++) {
								var param = params[i];
								var alias = param.getAnnotation(Alias.class);

								if (alias == null) {
									continue;
								}

								cob.localVariable(
									cob.parameterSlot(i),
									alias.value()[0],
									desc(param.getType()),
									cob.startLabel(), cob.endLabel()
								);
							}

							final var ret = expression.emit(context, cob);

							if (ret != void.class) {
								cob.return_(Primitive.primitiveLookup.getOrDefault(
									targetMethod.getReturnType(),
									Primitive.Unknown
								).trueType);
							}
						})
					);
				}
			);
	}

	@VisibleForTesting
	public static  <T> T define(MethodHandles.Lookup lookup, byte[] bytecode) throws Throwable {
		final var result = lookup.defineHiddenClass(bytecode, true);

		//noinspection unchecked
		return (T) result.findConstructor(result.lookupClass(), MethodType.methodType(void.class)).invoke();
	}

	public static <T> T compile(
		MethodHandles.Lookup lookup,
		Linker linker,
		Class<T> targetClass,
		Expression expression,
		String source
	) throws Throwable {
		byte[] bytecode = compileToBytecode(lookup, linker, targetClass, expression, source);

		return define(lookup, bytecode);
	}
}
