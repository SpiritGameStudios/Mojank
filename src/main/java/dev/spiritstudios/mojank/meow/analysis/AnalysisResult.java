package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.meow.Variables;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

public record AnalysisResult(StructType variables, Map<Expression, StructType> locals, @Nullable MethodHandles.Lookup variablesLookup) {
	public @Nullable Variables createVariables() {
		if (variablesLookup == null) return null;

		try {
			var constructor = variablesLookup.findConstructor(
				variablesLookup.lookupClass(),
				MethodType.methodType(void.class)
			);

			return (Variables) constructor.invoke();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
