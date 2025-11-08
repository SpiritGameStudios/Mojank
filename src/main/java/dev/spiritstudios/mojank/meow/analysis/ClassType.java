package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import org.jetbrains.annotations.NotNull;

public record ClassType(Class<?> clazz) implements Type {
	public static final ClassType CT_String = new ClassType(String.class);
	public static final ClassType CT_float = new ClassType(float.class);
	public static final ClassType CT_Float = new ClassType(Float.class);

	public static final ClassType CT_Object = new ClassType(Object.class);


	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append(clazz.descriptorString());
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
