package dev.spiritstudios.mojank.meow.link;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Ampflower
 **/
@Retention(RetentionPolicy.RUNTIME)
public @interface Alias {
	String[] value();
}
