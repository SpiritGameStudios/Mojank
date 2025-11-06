package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.meow.binding.Alias;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.reflect.Method;
import java.util.Map;

public record CompileContext(
	Method target,
	Map<String, IndexedParameter> parameters,
	Object2IntMap<String> locals
) {
	public static CompileContext of(Method method) {
		Map<String, IndexedParameter> map = new Object2ObjectOpenHashMap<>(method.getParameterCount());

		var parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			var param = parameters[i];

			final var alias = param.getAnnotation(Alias.class);
			if (alias == null) {
				continue;
			}

			var indexed = new IndexedParameter(param, i);
			for (final var str : alias.value()) {
				map.put(str, indexed);
			}
		}

		return new CompileContext(
			method,
			map,
			new Object2IntOpenHashMap<>()
		);
	}
}

