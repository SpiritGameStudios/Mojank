package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;

public record UnionType(List<Type> types) implements Type {
	public UnionType(Type... types) {
		this(new ArrayList<>(List.of(types)));
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append("Union[").pushIndent();

		for (Type type : types) {
			builder.newline();
			type.append(builder);
		}

		builder.popIndent().newline().append("]");
	}

	@Override
	public Class<?> clazz() {
		return Object.class;
	}

	@Override
	public ClassDesc desc() {
		return BoilerplateGenerator.desc(Object.class);
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
