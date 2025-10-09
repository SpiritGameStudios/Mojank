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
		final @Alias("c") Context context,
		final @Alias("q") Query query,
		final @Alias("v") Variable variable
	);
}
