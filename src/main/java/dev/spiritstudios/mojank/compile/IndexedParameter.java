package dev.spiritstudios.mojank.compile;

import java.lang.reflect.Parameter;

public record IndexedParameter(Parameter parameter, int index) {
	public Class<?> type() {
		return parameter.getType();
	}
}
