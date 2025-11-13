package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.internal.IndentedStringBuilder;
import dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.Map;

public record ClassType(Class<?> clazz) implements Type {
	public static final ClassType CT_void = new ClassType(void.class);
	public static final ClassType CT_boolean = new ClassType(boolean.class);
	public static final ClassType CT_byte = new ClassType(byte.class);
	public static final ClassType CT_short = new ClassType(short.class);
	public static final ClassType CT_char = new ClassType(char.class);
	public static final ClassType CT_int = new ClassType(int.class);
	public static final ClassType CT_long = new ClassType(long.class);
	public static final ClassType CT_float = new ClassType(float.class);
	public static final ClassType CT_double = new ClassType(double.class);
	public static final ClassType CT_Void = new ClassType(Void.class);
	public static final ClassType CT_Boolean = new ClassType(Boolean.class);
	public static final ClassType CT_Byte = new ClassType(Byte.class);
	public static final ClassType CT_Short = new ClassType(Short.class);
	public static final ClassType CT_Character = new ClassType(Character.class);
	public static final ClassType CT_Integer = new ClassType(Integer.class);
	public static final ClassType CT_Long = new ClassType(Long.class);
	public static final ClassType CT_Float = new ClassType(Float.class);
	public static final ClassType CT_Double = new ClassType(Double.class);
	public static final ClassType CT_Object = new ClassType(Object.class);
	public static final ClassType CT_String = new ClassType(String.class);

	private static final Map<Class<?>, ClassType> CLASS_TYPES = Map.ofEntries(
		Map.entry(void.class, CT_void),
		Map.entry(boolean.class, CT_boolean),
		Map.entry(byte.class, CT_byte),
		Map.entry(short.class, CT_short),
		Map.entry(char.class, CT_char),
		Map.entry(int.class, CT_int),
		Map.entry(long.class, CT_long),
		Map.entry(float.class, CT_float),
		Map.entry(double.class, CT_double),

		Map.entry(Void.class, CT_Void),
		Map.entry(Boolean.class, CT_Boolean),
		Map.entry(Byte.class, CT_Byte),
		Map.entry(Short.class, CT_Short),
		Map.entry(Character.class, CT_Character),
		Map.entry(Integer.class, CT_Integer),
		Map.entry(Long.class, CT_Long),
		Map.entry(Float.class, CT_Float),
		Map.entry(Double.class, CT_Double),

		Map.entry(Object.class, CT_Object),
		Map.entry(String.class, CT_String)
	);

	public static ClassType of(Class<?> clazz) {
		final var type = CLASS_TYPES.get(clazz);
		if (type != null) {
			return type;
		}
		return new ClassType(clazz);
	}

	@Override
	public void append(IndentedStringBuilder builder) {
		builder.append(clazz.descriptorString());
	}

	@Override
	public ClassDesc desc() {
		return BoilerplateGenerator.desc(clazz);
	}

	@Override
	public @NotNull String toString() {
		return toStr();
	}
}
