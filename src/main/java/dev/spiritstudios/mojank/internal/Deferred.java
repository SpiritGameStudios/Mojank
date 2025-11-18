package dev.spiritstudios.mojank.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Deferred<T> {
	private @Nullable T value;

	public void init(T value) {
		if (this.value != null) throw new IllegalStateException("Tried to init deferred twice!");
	}

	public @NotNull T get() {
		// WARNING: This won't actually run in production. Accessing a deferred value before its initialization will crash when the value is used!
		assert value != null;

		return value;
	}
}
