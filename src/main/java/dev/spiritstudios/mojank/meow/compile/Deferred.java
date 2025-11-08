package dev.spiritstudios.mojank.meow.compile;

/**
 * @author Ampflower
 **/
final class Deferred<T> {
	T value;

	boolean isPresent() {
		return this.value != null;
	}

	T get() {
		if (this.value == null) {
			throw new IllegalStateException("Collared too early");
		}
		return this.value;
	}
}
