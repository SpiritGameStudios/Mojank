package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.Collections;
import java.util.Map;

public record StructType(Map<String, Type> members) implements Type {
	public static final StructType EMPTY = new StructType(Collections.emptyMap());

	public StructType() {
		this(new Object2ObjectOpenHashMap<>());
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Struct[").pushIndent();

		members.forEach((name, type) -> {
			builder.newline().append("'").append(name).append("'").append(" -> ");
			type.append(builder);
		});

		builder.popIndent().newline().append("]");
	}

	@Override
	public Class<?> clazz() {
		return Variables.class;
	}

	@Override
	public ClassDesc desc() {
		return BoilerplateGenerator.desc(Variables.class);
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
