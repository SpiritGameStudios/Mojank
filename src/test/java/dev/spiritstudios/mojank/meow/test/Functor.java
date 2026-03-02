package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.compile.link.Alias;
import dev.spiritstudios.mojank.compile.link.Local;

/**
 * @author Ampflower
 **/
@FunctionalInterface
public interface Functor {
	@Local({"temp", "t"})
	float invoke(
		@Alias({"context", "c"}) Context context,
		@Alias({"query", "q"}) Query query
	);
}
