package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.meow.binding.Alias;
import dev.spiritstudios.mojank.meow.binding.Local;

/**
 * @author Ampflower
 **/
@FunctionalInterface
public interface Functor {

	@Local({"temp", "t"})
	int invoke(
		final @Alias({"context", "c"}) Context context,
		final @Alias({"query", "q"}) Query query,
		final @Alias({"variable", "v"}) Variable variable
	);
}
