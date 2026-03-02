package dev.spiritstudios.mojank.compile;

import dev.spiritstudios.mojank.compile.link.Alias;
import dev.spiritstudios.mojank.compile.link.Linker;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public record CompileContext(
	Linker linker,
	Method target,
	Map<String, IndexedParameter> parameters,
	Deque<Loop> loops
) {
	public CompileContext(Linker linker, Method target) {
		this(
			linker,
			target,
			new HashMap<>(),
			new ArrayDeque<>()
		);

		var methodParams = target.getParameters();

		for (int i = 0; i < methodParams.length; i++) {
			Parameter parameter = methodParams[i];

			Alias alias = parameter.getAnnotation(Alias.class);

			for (String name : alias.value()) {
				parameters.put(name, new IndexedParameter(parameter, i));
			}
		}
	}
}
