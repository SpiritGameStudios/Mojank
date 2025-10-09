package dev.spiritstudios.mojank.meow;

import dev.spiritstudios.mojank.internal.Util;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ObjectInput;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Ampflower
 **/
public final class Linker {
	private static final Logger logger = Util.logger();

	/**
	 * All packages here if left unchecked is potentially dangerous.
	 */
	private static final Set<String> _UntrustedPackages = Set.of(
		// Just generally dangerous.
		"java/lang/invoke",
		"java/lang/reflect",
		// Will cause problems if exposed.
		"jdk",
		"sun",
		// Not reasonable to audit.
		"com/sun",
		// Not that it is really dangerous, just useless.
		"java/lang/constant"
	);
	/**
	 * No script needs access to these classes.
	 */
	private static final Set<Class<?>> _UntrustedClasses = Set.of(
		Class.class,
		Process.class,
		Runtime.class,
		System.class,
		Instrumentation.class,
		ObjectInput.class,
		ObjectInputFilter.class,
		ObjectInputStream.class,
		ObjectOutput.class,
		ObjectOutputStream.class,
		Serializable.class
	);
	/**
	 * Probably dangerous, maybe should be blocked.
	 */
	private static final Set<String> _IoPackages = Set.of(
		"java/io",
		"java/net",
		"javax/net",
		"java/nio",
		"jdk/nio"
	);
	/**
	 * Classes that can't cause harm on their own.
	 */
	private static final Set<Class<?>> _SafeClasses = Set.of(
		Math.class,
		StrictMath.class,
		String.class,
		Void.class,
		Boolean.class,
		Byte.class,
		Short.class,
		Integer.class,
		Long.class,
		Float.class,
		Double.class,
		void.class,
		boolean.class,
		byte.class,
		short.class,
		int.class,
		long.class,
		float.class,
		double.class,
		BigInteger.class,
		BigDecimal.class,
		MathContext.class
	);

	public static final Linker untrusted = new Linker.Builder()
		.addBlockedPackages(_UntrustedPackages, _IoPackages)
		.addBlockedClasses(_UntrustedClasses)
		.addAllowedClasses(_SafeClasses)
		.build();

	private final @Nullable Set<String> blockedPackages;
	private final @Nullable Set<String> allowedPackages;
	private final @Nullable Set<Class<?>> blockedClasses;
	private final @Nullable Set<Class<?>> allowedClasses;

	private transient final Map<Class<?>, Boolean> permitted = new WeakHashMap<>();

	private Linker(
		final @Nullable Set<String> blockedPackages,
		final @Nullable Set<String> allowedPackages,
		final @Nullable Set<Class<?>> blockedClasses,
		final @Nullable Set<Class<?>> allowedClasses
	) {
		this.blockedPackages = blockedPackages;
		this.allowedPackages = allowedPackages;
		this.blockedClasses = blockedClasses;
		this.allowedClasses = allowedClasses;

		boolean allowlist = false;

		if (allowedClasses != null) {
			allowlist = true;
			final var test = new ArrayList<>(allowedClasses);
			test.retainAll(_UntrustedClasses);
			if (!test.isEmpty()) {
				logger.error("This linker contains known unsafe classes: {}", test, new Throwable());
			}
		}

		if (allowedPackages != null) {
			allowlist = true;
			final var test = allowedPackages.stream()
				.filter(str -> _UntrustedPackages.stream().anyMatch(str::startsWith))
				.toList();

			if (!test.isEmpty()) {
				logger.error("This linker contains known unsafe packages: {}", test, new Throwable());
			}
		}

		if (allowlist) {
			// Avoid warning fatigue.
			return;
		}

		logger.warn(
			"This linker is blocklist only. This may allow your scripts to sandbox escape, especially when paired with libraries.",
			new Throwable()
		);

		if (blockedPackages == null || !blockedPackages.containsAll(_UntrustedPackages)) {
			logger.error(
				"This linker is allowing untrusted packages. This will allow your scripts to sandbox escape.",
				new Throwable()
			);
		}

		if (blockedClasses == null || !blockedClasses.containsAll(_UntrustedClasses)) {
			logger.error(
				"This linker is allowing untrusted classes. This will allow your scripts to sandbox escape.",
				new Throwable()
			);
		}
	}

	boolean isPermitted(Class<?> clazz) {
		return permitted.computeIfAbsent(clazz, this::isPermitted0);
	}

	boolean isPermitted(Class<?>... classes) {
		for (final Class<?> clazz : classes) {
			if (!isPermitted(clazz)) {
				logger.debug("Blocking {} in {}; linker: {}", clazz, classes, this);
				return false;
			}
		}
		return true;
	}

	private boolean isPermitted0(Class<?> clazz) {
		if (blockedClasses != null && blockedClasses.contains(clazz)) {
			return false;
		}

		if (allowedClasses != null) {
			return allowedClasses.contains(clazz);
		}

		final var blockedPackages = Objects.requireNonNullElse(this.blockedPackages, Set.of());

		final String pak = clazz.getPackage().getName().replace('.', '/');
		int i = pak.length();
		while ((i = pak.lastIndexOf('/', i)) >= 0) {
			final String sub = pak.substring(0, i);
			if (blockedPackages.contains(sub)) {
				return false;
			}

			if (allowedPackages != null && allowedPackages.contains(sub)) {
				return true;
			}
		}

		return allowedPackages == null;
	}

	public Builder toBuilder() {
		return new Builder()
			.allowedPackages(this.allowedPackages)
			.blockedPackages(this.blockedPackages)
			.allowedClasses(this.allowedClasses)
			.blockedClasses(this.blockedClasses);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final Linker linker = (Linker) o;
		return Objects.equals(
			blockedPackages,
			linker.blockedPackages
		) && Objects.equals(allowedPackages, linker.allowedPackages) && Objects.equals(
			blockedClasses,
			linker.blockedClasses
		) && Objects.equals(allowedClasses, linker.allowedClasses);
	}

	@Override
	public int hashCode() {
		return Objects.hash(blockedPackages, allowedPackages, blockedClasses, allowedClasses);
	}

	@Override
	public String toString() {
		return "Linker{" +
			   "blockedPackages=" + blockedPackages +
			   ", allowedPackages=" + allowedPackages +
			   ", blockedClasses=" + blockedClasses +
			   ", allowedClasses=" + allowedClasses +
			   ", permitted=" + permitted +
			   '}';
	}

	@CheckReturnValue
	public Field findField(final Class<?> context, final String toAccess) {
		throw new UnsupportedOperationException();
	}

	public static final class Builder {
		private @Nullable Set<String> blockedPackages;
		private @Nullable Set<String> allowedPackages;
		private @Nullable Set<Class<?>> blockedClasses;
		private @Nullable Set<Class<?>> allowedClasses;

		@SafeVarargs
		private static <T> Set<T> concat(Collection<T> a, Collection<T>... b) {
			final Set<T> set = new HashSet<>();
			if (a != null) {
				set.addAll(a);
			}
			if (b != null) {
				for (final var c : b) {
					if (c != null) {
						set.addAll(c);
					}
				}
			}
			return set;
		}

		private static <T> @Nullable Set<T> nullableCopy(@Nullable Collection<T> collection) {
			if (collection == null) {
				return null;
			}
			return Set.copyOf(collection);
		}


		@SafeVarargs
		@CheckReturnValue
		public final Builder addBlockedPackages(Collection<String>... blockedPackages) {
			this.blockedPackages = concat(this.blockedPackages, blockedPackages);
			return this;
		}

		@SafeVarargs
		@CheckReturnValue
		public final Builder addAllowedPackages(Collection<String>... allowedPackages) {
			this.allowedPackages = concat(this.allowedPackages, allowedPackages);
			return this;
		}

		@SafeVarargs
		@CheckReturnValue
		public final Builder addBlockedClasses(Collection<Class<?>>... blockedClasses) {
			this.blockedClasses = concat(this.blockedClasses, blockedClasses);
			return this;
		}

		@SafeVarargs
		@CheckReturnValue
		public final Builder addAllowedClasses(Collection<Class<?>>... allowedClasses) {
			this.allowedClasses = concat(this.allowedClasses, allowedClasses);
			return this;
		}


		@CheckReturnValue
		public Builder addBlockedPackages(String... blockedPackages) {
			this.blockedPackages = concat(this.blockedPackages, Arrays.asList(blockedPackages));
			return this;
		}

		@CheckReturnValue
		public Builder addAllowedPackages(String... allowedPackages) {
			this.allowedPackages = concat(this.allowedPackages, Arrays.asList(allowedPackages));
			return this;
		}

		@CheckReturnValue
		public Builder addBlockedClasses(Class<?>... blockedClasses) {
			this.blockedClasses = concat(this.blockedClasses, Arrays.asList(blockedClasses));
			return this;
		}

		@CheckReturnValue
		public Builder addAllowedClasses(Class<?>... allowedClasses) {
			this.allowedClasses = concat(this.allowedClasses, Arrays.asList(allowedClasses));
			return this;
		}


		@CheckReturnValue
		public Builder blockedPackages(Set<String> blockedPackages) {
			this.blockedPackages = blockedPackages;
			return this;
		}

		@CheckReturnValue
		public Builder allowedPackages(Set<String> allowedPackages) {
			this.allowedPackages = allowedPackages;
			return this;
		}

		@CheckReturnValue
		public Builder blockedClasses(Set<Class<?>> blockedClasses) {
			this.blockedClasses = blockedClasses;
			return this;
		}

		@CheckReturnValue
		public Builder allowedClasses(Set<Class<?>> allowedClasses) {
			this.allowedClasses = allowedClasses;
			return this;
		}


		Set<String> blockedPackages() {
			return blockedPackages;
		}

		Set<String> allowedPackages() {
			return allowedPackages;
		}

		Set<Class<?>> blockedClasses() {
			return blockedClasses;
		}

		Set<Class<?>> allowedClasses() {
			return allowedClasses;
		}

		@CheckReturnValue
		public Linker build() {
			return new Linker(
				nullableCopy(blockedPackages),
				nullableCopy(allowedPackages),
				nullableCopy(blockedClasses),
				nullableCopy(allowedClasses)
			);
		}
	}


}
