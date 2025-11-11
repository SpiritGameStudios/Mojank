package dev.spiritstudios.mojank.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * @author Ampflower
 */
public final class Util {
	public static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	public static Logger logger() {
		return LoggerFactory.getLogger(walker.getCallerClass());
	}

	public static <T> T make(Supplier<T> maker) {
		return maker.get();
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> cast(Class<?> clazz) {
		return (Class<T>) clazz;
	}
}
