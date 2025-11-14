package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.meow.analysis.Analyser;
import dev.spiritstudios.mojank.meow.analysis.AnalysisResult;
import dev.spiritstudios.mojank.meow.binding.Alias;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.CheckReturnValue;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author Ampflower
 **/
public final class CompilerFactory<T> {
	private final MethodHandles.Lookup lookup;
	private final Class<T> type;

	private final Method targetMethod;

	private final Map<String, IndexedParameter> parameters;
	private final int variablesIndex;

	private final Linker linker;

	public CompilerFactory(
		final MethodHandles.Lookup lookup,
		final Class<T> type, Linker linker
	) {
		this.lookup = lookup;
		this.type = type;
		this.linker = linker;

		this.targetMethod = linker.tryFunctionalClass(type)
			.orElseThrow(() -> new IllegalArgumentException("clazz " + this.type + " has no suitable methods"));

		this.parameters = new Object2ObjectOpenHashMap<>(targetMethod.getParameterCount());

		var methodParams = targetMethod.getParameters();
		for (int i = 0; i < methodParams.length; i++) {
			var param = methodParams[i];

			final var alias = param.getAnnotation(Alias.class);
			if (alias == null) {
				continue;
			}

			var indexed = new IndexedParameter(param, i);
			for (final var str : alias.value()) {
				parameters.put(str, indexed);
			}
		}
		this.variablesIndex = methodParams.length;
	}

	public Analyser createAnalyser() {
		return new Analyser(parameters, linker);
	}

	@CheckReturnValue
	public Compiler<T> build(AnalysisResult result) {
		return new Compiler<>(
			lookup,
			type,
			linker,
			targetMethod,
			parameters,
			variablesIndex,
			result
		);
	}
}
