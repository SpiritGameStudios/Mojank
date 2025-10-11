package dev.spiritstudios.mojank.meow.binding;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Ampflower
 **/
@Retention(RetentionPolicy.RUNTIME)
public @interface Local {
	String[] value();
}
