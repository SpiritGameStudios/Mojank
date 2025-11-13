package dev.spiritstudios.mojank.internal;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Map.Entry} implementation binding get & set
 *
 * @author Ampflower
 */
// Used by BoilerplateGenerator: accessed by *code gen*
@SuppressWarnings("unused")
public record VariableEntry(
	@NotNull
	String key,
	@NotNull
	MethodHandle get,
	@NotNull
	MethodHandle set
) implements Map.Entry<String, Object> {

	public VariableEntry {
		Objects.requireNonNull(key, "key");
		{
			// implicit null check
			if (get.describeConstable().isPresent()) {
				throw new IllegalArgumentException(get + " is not bound. Call InvokeVirtual on MethodHandle#bindTo(this) before constructing.");
			}
			final var type = get.type();
			if (type.returnType() == void.class || type.parameterCount() != 0) {
				throw new IllegalArgumentException(get + " is not a getter.");
			}
		}
		{
			// implicit null check
			if (set.describeConstable().isPresent()) {
				throw new IllegalArgumentException(set + " is not bound. Call InvokeVirtual on MethodHandle#bindTo(this) before constructing.");
			}
			final var type = set.type();
			if (type.returnType() != void.class || type.parameterCount() != 1) {
				throw new IllegalArgumentException(set + " is not a setter.");
			}
		}
	}

	@Override
	public String getKey() {
		return this.key;
	}

	@Override
	public Object getValue() {
		try {
			return this.get().invoke();
		} catch (Error e) {
			throw e;
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	@Override
	public Object setValue(final Object value) {
		try {
			final var old = this.get().invoke();
			this.set().invoke(value);
			return old;
		} catch (Error e) {
			throw e;
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	/**
	 * Standard {@link Map.Entry#equals(Object)} implementation. Record's implementation is ill-suited.
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Map.Entry<?, ?> other) {
			return this.getKey().equals(other.getKey()) && Objects.equals(this.getValue(), other.getValue());
		}
		return false;
	}

	/**
	 * Standard {@link Map.Entry#hashCode()} implementation. Record's implementation is ill-suited.
	 */
	@Override
	public int hashCode() {
		return this.getKey().hashCode() ^ Objects.hashCode(this.getValue());
	}

	/**
	 * Helper toString hiding the true
	 */
	@Override
	public String toString() {
		return "VarEntry {" + this.getKey() + "=" + this.getValue() + "}";
	}
}
