package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.binding.Alias;
import dev.spiritstudios.mojank.meow.binding.Local;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ampflower
 **/
@FunctionalInterface
public interface Functor {
	@Local({"temp", "t"})
	float invoke(
		@Alias({"context", "c"}) Context context,
		@Alias({"query", "q"}) Query query,
		@Alias({"variable", "v"}) @Nullable Variables variable
	);
}
