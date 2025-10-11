package dev.spiritstudios.mojank.meow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author Ampflower
 **/
record Prototype(
	Class<?> type,
	Map<String, List<Method>> allowedMethods,
	Map<String, List<Field>> allowedFields,
	boolean permitted
) {
}
