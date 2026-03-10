package dev.spiritstudios.mojank.compile;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.constant.ConstantDescs.CD_Boolean;
import static java.lang.constant.ConstantDescs.CD_Byte;
import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_Character;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Double;
import static java.lang.constant.ConstantDescs.CD_Float;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Short;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_Void;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_short;

public class Descriptors {
	private static final Map<Class<?>, ClassDesc> CLASS_DESCS = Map.ofEntries(
		Map.entry(void.class, CD_void),
		Map.entry(boolean.class, CD_boolean),
		Map.entry(byte.class, CD_byte),
		Map.entry(short.class, CD_short),
		Map.entry(char.class, CD_char),
		Map.entry(int.class, CD_int),
		Map.entry(long.class, CD_long),
		Map.entry(float.class, CD_float),
		Map.entry(double.class, CD_double),

		Map.entry(Void.class, CD_Void),
		Map.entry(Boolean.class, CD_Boolean),
		Map.entry(Byte.class, CD_Byte),
		Map.entry(Short.class, CD_Short),
		Map.entry(Character.class, CD_Character),
		Map.entry(Integer.class, CD_Integer),
		Map.entry(Long.class, CD_Long),
		Map.entry(Float.class, CD_Float),
		Map.entry(Double.class, CD_Double),

		Map.entry(Object.class, CD_Object),
		Map.entry(String.class, CD_String),

		Map.entry(Class.class, CD_Class),
		Map.entry(MethodHandle.class, CD_MethodHandle),
		Map.entry(CallSite.class, CD_CallSite)

	);

	private static final Map<Class<?>, ClassDesc> descCache = new IdentityHashMap<>();

	public static ClassDesc desc(Class<?> clazz) {
		final var desc = CLASS_DESCS.get(clazz);
		if (desc != null) {
			return desc;
		}
		return descCache.computeIfAbsent(clazz, Descriptors::desc0);
	}

	private static ClassDesc desc0(Class<?> clazz) {
		return clazz.describeConstable().orElseThrow();
	}

	public static MethodTypeDesc methodDesc(Class<?> ret) {
		return MethodTypeDesc.of(ret.describeConstable().orElseThrow());
	}

	public static MethodTypeDesc methodDesc(Class<?> ret, Class<?>... args) {
		return MethodTypeDesc.of(
			ret.describeConstable().orElseThrow(),
			Arrays.stream(args)
				.map(clazz -> clazz.describeConstable().orElseThrow())
				.toList()
		);
	}
}
