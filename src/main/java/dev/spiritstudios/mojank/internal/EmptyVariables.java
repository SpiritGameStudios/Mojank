package dev.spiritstudios.mojank.internal;

import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.link.Linker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Ampflower
 **/
public final class EmptyVariables implements Variables {
	@Linker.Hidden
	public static final Variables INSTANCE = new EmptyVariables();

	@Linker.Hidden
	public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup()
		.dropLookupMode(MethodHandles.Lookup.PUBLIC);

	private EmptyVariables() {
		if (INSTANCE != null) {
			throw new IllegalCallerException();
		}
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public boolean containsValue(final Object value) {
		return false;
	}

	@Override
	public Object get(final Object key) {
		return null;
	}

	@Override
	public @Nullable Object put(final String key, final Object value) {
		throw new IllegalArgumentException("cannot store " + key + ": not a field");
	}

	@Override
	public CallSite getCallSiteOf(final String key) {
		return Variables.NULL_CALL_SITE;
	}

	@Override
	public CallSite setCallSiteOf(final String key) {
		throw new IllegalArgumentException("cannot store " + key + ": not a field");
	}

	@Override
	public MethodHandle getGetter(final String key) {
		return Variables.NULL_HANDLE;
	}

	@Override
	public MethodHandle getSetter(final String key) {
		throw new IllegalArgumentException("cannot store " + key + ": not a field");
	}

	@Override
	public void putAll(@NotNull final Map<? extends String, ?> m) {
		throw new IllegalArgumentException("cannot store " + m + ": not a field");
	}

	@Override
	public @NotNull Set<String> keySet() {
		return Collections.emptySet();
	}

	@NotNull
	@Override
	public Collection<Object> values() {
		return Collections.emptySet();
	}

	@Override
	public @NotNull Set<Entry<String, Object>> entrySet() {
		return Collections.emptySet();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Map<?, ?> map) {
			return map.isEmpty();
		}
		return false;
	}

	@Override
	public int hashCode() {
		// Nothing to hash.
		return 0;
	}

	@Override
	public String toString() {
		return "Variables {}";
	}
}
