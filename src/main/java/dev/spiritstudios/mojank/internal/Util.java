package dev.spiritstudios.mojank.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ampflower
 */
public final class Util {
	public static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	public static Logger logger() {
		return LoggerFactory.getLogger(walker.getCallerClass());
	}
}
