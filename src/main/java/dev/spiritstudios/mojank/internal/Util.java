package dev.spiritstudios.mojank.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Ampflower
 */
public final class Util {
	public static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	public static Logger logger() {
		return LoggerFactory.getLogger(walker.getCallerClass());
	}

	public static <T> T make(T thing, Consumer<T> maker) {
		maker.accept(thing);

		return thing;
	}

	public static <T> T make(Supplier<T> maker) {
		return maker.get();
	}

	public static String formatDuration(Duration duration) {
		TimeUnit unit = TimeUnit.NANOSECONDS;
		if (duration.toDays() > 0) unit = TimeUnit.DAYS;
		else if (duration.toHours() > 0) unit = TimeUnit.HOURS;
		else if (duration.toMinutes() > 0) unit = TimeUnit.MINUTES;
		else if (duration.toSeconds() > 0) unit = TimeUnit.SECONDS;
		else if (duration.toMillis() > 0) unit = TimeUnit.MILLISECONDS;

		double time = (double) duration.toNanos() / TimeUnit.NANOSECONDS.convert(1, unit);

		return time + switch (unit){
			case NANOSECONDS -> "ns";
			case MILLISECONDS -> "ms";
			case SECONDS -> "s";
			case MINUTES -> "m";
			case HOURS -> "h";
			case DAYS -> "d";
			default -> "?";
		};
	}

}
