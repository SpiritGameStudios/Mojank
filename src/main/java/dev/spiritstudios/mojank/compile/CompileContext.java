package dev.spiritstudios.mojank.compile;

import dev.spiritstudios.mojank.compile.link.Alias;
import dev.spiritstudios.mojank.compile.link.Linker;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public record CompileContext(
	Linker linker,
	Method target,
	List<Parameter> parameters,
	Map<String, IndexedParameter> parametersByName,
	Deque<Loop> loops
) {
	public CompileContext(Linker linker, Method target) {
		this(
			linker,
			target,
			new ArrayList<>(),
			new HashMap<>(),
			new ArrayDeque<>()
		);

		var methodParams = target.getParameters();

		for (int i = 0; i < methodParams.length; i++) {
			Parameter parameter = methodParams[i];

			Alias alias = parameter.getAnnotation(Alias.class);
			parameters.add(i, parameter);

			for (String name : alias.value()) {
				parametersByName.put(name, new IndexedParameter(parameter, i + 1));
			}
		}
	}
}
