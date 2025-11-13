package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;

import java.lang.constant.ClassDesc;

public sealed interface Type permits ClassType, StructType {
	void append(IndentedStringBuilder builder);

	default String toStr() {
		var builder = new IndentedStringBuilder(new StringBuilder());
		append(builder);
		return builder.toString();
	}

	Class<?> clazz();

	ClassDesc desc();
}
