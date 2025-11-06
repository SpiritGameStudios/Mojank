package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.meow.binding.Alias;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CompileContext {
	public final Method target;
	public final ClassDesc desc;

	public final Map<String, IndexedParameter> parameters;
	public final Object2IntMap<String> locals = new Object2IntOpenHashMap<>();

	public final int variablesIndex;

	public final ClassBuilder variableBuilder;

	public CompileContext(
		Method target,
		ClassDesc desc,
		ClassBuilder variableBuilder
	) {
		this.parameters = new Object2ObjectOpenHashMap<>(target.getParameterCount());

		var methodParams = target.getParameters();
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

		this.target = target;
		this.desc = desc;
		this.variableBuilder = variableBuilder;
	}
}

