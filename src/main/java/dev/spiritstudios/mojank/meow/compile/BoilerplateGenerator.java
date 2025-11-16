package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.internal.EmptyVariables;
import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.analysis.StructType;
import dev.spiritstudios.mojank.meow.analysis.Type;
import dev.spiritstudios.mojank.runtime.MeowBootstraps;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.CodeBuilder;
import org.glavo.classfile.Label;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.instruction.SwitchCase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_byte;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_short;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static java.lang.constant.ConstantDescs.ofCallsiteBootstrap;

/**
 * @author Ampflower
 **/
@ApiStatus.Internal
public final class BoilerplateGenerator {
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
		Map.entry(CallSite.class, CD_CallSite),

		Map.entry(Variables.class, desc0(Variables.class))
	);

	private static final Map<Class<?>, ClassDesc> descCache = new IdentityHashMap<>();

	static void tryCast(Class<?> from, Class<?> to, CodeBuilder builder) {
		if (from != void.class) {
			Primitive.convert(builder, from, to);
		}
	}

	static ConstantDesc tryCast(ConstantDesc in, Class<?> out) {
		if (out == float.class) {
			return in;
		}

		if (out == long.class) {
			return (long) in;
		} else if (out == int.class) {
			return (int) in;
		} else if (out == double.class) {
			return (double) in;
		} else if (out == boolean.class) {
			return ((float) in) != 0 ? 1 : 0;
		}

		throw new ClassCastException("Cannot cast float to " + out);
	}

	/**
	 * Generates a stub CompilerResult, this contains most important functions and class structure, excluding the main invoker.
	 */
	static void writeCompilerResultStub(
		final ClassDesc self,
		final Class<?> soup,
		final Method target,
		final String source,
		final ClassBuilder builder
	) {
		var owner = soup.isInterface() ?
			CD_Object :
			desc(soup);


		builder
			.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);

		if (soup.isInterface()) {
			builder.withInterfaceSymbols(
				desc(soup),
				desc(CompilerResult.class)
			);
		} else {
			builder
				.withSuperclass(desc(soup))
				.withInterfaceSymbols(desc(CompilerResult.class));
		}

		// Constructor
		generateConstructor(builder, owner);

		// Object.equals
		builder.withMethodBody(
			"equals",
			MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z"),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.aload(0) // push this
				.aload(1) // push other
				.ifThenElse( // if (this == other) (pop this and other)
					Opcode.IF_ACMPEQ,
					eq ->
						eq.iconst_1()
							.ireturn(), // return true,
					ne ->
						ne
							.aload(1) // push other
							.instanceof_(desc(CompilerResult.class)) // pop other, push bool
							.ifThenElse(
								Opcode.IFNE,
								eq -> eq
									.aload(1)
									.invokevirtual(
										CD_Object,
										"toString",
										MethodTypeDesc.ofDescriptor("()Ljava/lang/String;")
									)
									.ldc(source)
									.invokevirtual(
										CD_String,
										"equals",
										MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z")
									)
									.ireturn(),
								ne1 ->
									ne1
										.iconst_0()
										.ireturn()
							)
				)
		);

		builder.withMethodBody(
			"hashCode",
			MethodTypeDesc.ofDescriptor("()I"),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(source.hashCode())
				.ireturn()
		);

		builder.withMethodBody(
			"getType",
			methodDesc(Class.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(desc(soup))
				.areturn()
		);

		builder.withMethodBody(
			"toHandle",
			methodDesc(MethodHandle.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(
					MethodHandleDesc.ofMethod(
						DirectMethodHandleDesc.Kind.VIRTUAL,
						self,
						target.getName(),
						methodDesc(
							target.getReturnType(),
							target.getParameterTypes()
						)
					)
				)
				.aload(0)
				.invokevirtual(
					desc(MethodHandle.class),
					"bindTo",
					methodDesc(MethodHandle.class, Object.class)
				)
				.areturn()
		);

		builder.withMethodBody(
			"toString",
			methodDesc(String.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(source)
				.areturn()
		);
	}

	private record Field(String name, MethodHandles.Lookup lookup) {
	}

	public static String loopIndexName(int depth) {
		return String.valueOf((char) ((int) 'i' + depth)); // If you have a loop nested over 17 layers deep then that's your fault.
	}

	public static MethodHandles.Lookup writeVariablesClass(
		MethodHandles.Lookup lookup,
		final ClassDesc self,
		final StructType variables
	) throws IllegalAccessException {
		if (variables.members().isEmpty()) {
			// Special-cased by MeowBootstraps
			return EmptyVariables.LOOKUP;
		}

		List<Field> fields = new ArrayList<>();

		byte[] bytecode = compileVariables(lookup, self, variables, fields);

		return lookup.defineHiddenClassWithClassData(
			bytecode,
			fields.stream().map(Field::lookup).toList(),
			true
		);
	}

	@VisibleForTesting
	public static byte[] compileVariables(
		MethodHandles.Lookup lookup,
		ClassDesc self,
		StructType variables,
		List<Field> fields
	) {
		return ClassFile.of().build(
			self,
			builder -> {
				builder
					.withFlags(AccessFlag.FINAL, AccessFlag.PUBLIC)
					.withInterfaceSymbols(desc(Variables.class));

				final StringBuilder nameBuilder = new StringBuilder();
				final ClassDesc[] descs = new ClassDesc[variables.members().size()];

				int descIndex = 0;
				for (final var entry : variables.members().entrySet()) {
					final var name = entry.getKey();
					final var type = entry.getValue();
					final var desc = type.desc();
					descs[descIndex++] = desc;

					builder.withField(name, desc, ClassFile.ACC_PUBLIC);

					nameBuilder.append(name).append(" = \u0001, ");

					if (type instanceof StructType struct) {
						try {
							fields.add(new Field(
								name,
								writeVariablesClass(lookup, self.nested("Struct"), struct)
							));
						} catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					}
				}

				builder.withMethodBody(
					INIT_NAME,
					MTD_void,
					ClassFile.ACC_PUBLIC,
					cob -> {
						cob
							.aload(0) // push this
							.invokeInstruction(
								Opcode.INVOKESPECIAL,
								CD_Object,
								INIT_NAME,
								MTD_void,
								false
							); // call super

						for (int i = 0; i < fields.size(); i++) {
							var field = fields.get(i);

							cob
								.aload(0)
								.invokedynamic(MeowBootstraps.constructOfIndexed(i))
								.putfield(
									self,
									field.name,
									desc(Variables.class)
								);
						}

						cob.return_();
					}
				);

				if (nameBuilder.isEmpty()) {
					throw new AssertionError("nameBuilder was not populated.");
				}

				final String stringTemplate = nameBuilder
					.insert(0, "\u0002 {")
					.replace(nameBuilder.length() - 2, nameBuilder.length(), "}")
					.toString();

				builder.withMethodBody(
					"toString",
					methodDesc(String.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					cob -> {
						for (final var entry : variables.members().entrySet()) {
							cob.aload(0).getfield(self, entry.getKey(), entry.getValue().desc());
						}

						cob.invokedynamic(DynamicCallSiteDesc.of(
								ofCallsiteBootstrap(
									desc(StringConcatFactory.class),
									"makeConcatWithConstants",
									CD_CallSite,
									CD_String,
									desc(Object[].class)
								),
								"makeConcatWithConstants",
								MethodTypeDesc.of(
									CD_String,
									Objects.requireNonNull(descs, "descs")
								),
								stringTemplate,
								self
							))
							.areturn();
					}
				);


				builder.withMethodBody(
					"hashCode",
					methodDesc(int.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					// FIXME: adhere to map contract
					cob -> {
						int j = 0;
						for (final var entry : variables.members().entrySet()) {


							if (j != 0) {
								cob.bipush(31).imul();
							}

							final var type = entry.getValue().desc();
							cob.aload(0)
								.getfield(self, entry.getKey(), type);

							if (entry.getValue().clazz() == float.class) {
								cob.invokestatic(CD_Float, "hashCode", MethodTypeDesc.of(CD_int, CD_float));
							} else {
								cob.invokevirtual(type, "hashCode", MethodTypeDesc.of(CD_int));
							}

							if (j++ != 0) {
								cob.iadd();
							}
						}
						cob.ireturn();
					}
				);

				builder.withMethodBody(
					"equals",
					methodDesc(boolean.class, Object.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					// FIXME: adhere to map contract
					cob -> cob
						.aload(1)
						.instanceof_(self)
						.ifThen(ifInst -> {
							// So we don't need to checkcast every time.
							ifInst.aload(1).checkcast(self).astore(1);
							final var itr = variables.members().entrySet().iterator();
							while (itr.hasNext()) {
								final var entry = itr.next();

								ifInst.aload(0)
									.getfield(self, entry.getKey(), entry.getValue().desc())
									.aload(1)
									.getfield(self, entry.getKey(), entry.getValue().desc());

								// TODO: polymorphic callsites
								final var type = entry.getValue().clazz();
								if (type.isPrimitive()) {
									// Primitive special casing.
									if (type == void.class) {
										// Hello, who was the troll to put void in here?
										throw new AssertionError(entry);
									}
									if (type == float.class) {
										ifInst.fcmpg().ifne(ifInst.breakLabel());
									} else if (type == double.class) {
										ifInst.dcmpg().ifne(ifInst.breakLabel());
									} else {
										ifInst.if_icmpne(ifInst.breakLabel());
									}
								} else {
									// For the sake of simplicitly, fork out to Objects.equals()
									// Having to ifnonnull then call is annoying and costly in bytecode size.
									ifInst.invokestatic(
										desc(Objects.class),
										"equals",
										MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object)
									);

									// We can use this as a terminator if is no next entry.
									if (itr.hasNext()) {
										ifInst.ifne(ifInst.breakLabel());
									} else {
										ifInst.ireturn();
										return; // Break out of the lambda
									}
								}
							}
							// We can finally return true.
							ifInst.iconst_1().ireturn();
						})
						.iconst_0()
						.ireturn()
				);

				// The rest are map API
				// Hardcoded value; fields cannot be created or destroyed.
				builder.withMethodBody(
					"size",
					methodDesc(int.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					cob -> cob
						.constantInstruction(variables.members().size())
						.ireturn()
				);

				// TODO: containsValue

				builder.withMethodBody(
					"get",
					methodDesc(Object.class, String.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					cob -> cob
						.aload(0)
						.aload(1)
						.instanceof_(CD_String)
						.ifThen(
							Opcode.IFNE, sw -> {
								final var values = variables.members()
									.entrySet()
									.toArray(i -> (Map.Entry<String, Type>[]) new Map.Entry[i]);
								final var labels = new SwitchCase[variables.members().size()];
								final Label pop1 = sw.newLabel();

								for (int i = 0; i < values.length; i++) {
									labels[i] = SwitchCase.of(values[i].getKey().hashCode(), sw.newLabel());
								}

								sw.aload(1).dup()
									.invokevirtual(CD_String, "hashCode", methodDesc(int.class))
									.lookupswitch(pop1, Arrays.asList(labels));

								for (int i = 0; i < values.length; i++) {
									sw.labelBinding(labels[i].target())
										.ldc(values[i].getKey())
										.invokevirtual(CD_String, "equals", methodDesc(boolean.class, Object.class))
										.ifeq(sw.breakLabel())
										.getfield(self, values[i].getKey(), values[i].getValue().desc());

									Primitive.box(sw, values[i].getValue().clazz());

									sw.areturn();
								}

								sw
									.labelBinding(pop1)
									.pop();
							}
						)
						.aconst_null()
						.areturn()
				);

				builder.withMethodBody(
					"getGetter",
					methodDesc(MethodHandle.class, String.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					cob -> cob
						.aload(1)
						.ifThen(
							Opcode.IFNONNULL, ifBlock -> lookupSwitchOf(
								ifBlock,
								ifBlock.breakLabel(),
								variables.members(),
								v -> v.aload(1),
								(entry, e) -> e.ldc(MethodHandleDesc.ofField(
									DirectMethodHandleDesc.Kind.GETTER,
									self,
									entry.getKey(),
									entry.getValue().desc()
								)).goto_(e.breakLabel())
							).aload(0)
								.invokevirtual(CD_MethodHandle, "bindTo", methodDesc(MethodHandle.class, Object.class))
								.areturn()
						)
						.new_(desc(IllegalArgumentException.class))
						.dup()
						.invokespecial(desc(IllegalArgumentException.class), "<init>", methodDesc(void.class))
						.athrow()
				);

				builder.withMethodBody(
					"getSetter",
					methodDesc(MethodHandle.class, String.class),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
					cob -> cob
						.aload(1)
						.ifThen(
							Opcode.IFNONNULL, ifBlock -> lookupSwitchOf(
								ifBlock,
								ifBlock.breakLabel(),
								variables.members(),
								v -> v.aload(1),
								(entry, e) -> e.ldc(MethodHandleDesc.ofField(
									DirectMethodHandleDesc.Kind.SETTER,
									self,
									entry.getKey(),
									entry.getValue().desc()
								)).goto_(e.breakLabel())
							).aload(0)
								.invokevirtual(CD_MethodHandle, "bindTo", methodDesc(MethodHandle.class, Object.class))
								.areturn()
						)
						.new_(desc(IllegalArgumentException.class))
						.dup()
						.invokespecial(desc(IllegalArgumentException.class), "<init>", methodDesc(void.class))
						.athrow()
				);
			}
		);
	}

	public static void generateConstructor(ClassBuilder builder, ClassDesc owner) {
		builder.withMethodBody(
			INIT_NAME,
			MTD_void,
			ClassFile.ACC_PRIVATE,
			cob -> cob
				.aload(0) // push this
				.invokeInstruction(
					Opcode.INVOKESPECIAL,
					owner,
					INIT_NAME,
					MTD_void,
					false
				)
				.return_()
		);
	}

	public static <T extends CodeBuilder> T lookupSwitchOf(
		final T builder,
		final Label fallback,
		final Map<String, Type> members,
		final Consumer<T> pushOperand,
		final BiConsumer<Map.Entry<String, Type>, CodeBuilder.BlockCodeBuilder> consumer
	) {
		final var values = members
			.entrySet()
			.toArray(i -> (Map.Entry<String, Type>[]) new Map.Entry[i]);
		final var labels = new SwitchCase[members.size()];

		for (int i = 0; i < values.length; i++) {
			labels[i] = SwitchCase.of(values[i].getKey().hashCode(), builder.newLabel());
		}

		pushOperand.accept(builder);

		builder
			.invokevirtual(CD_Object, "hashCode", methodDesc(int.class))
			.lookupswitch(fallback, Arrays.asList(labels));

		builder.block(b -> {
			for (int i = 0; i < values.length; i++) {
				builder.labelBinding(labels[i].target());
				consumer.accept(values[i], b);
			}
		});

		return builder;
	}

	public static ClassDesc desc(Class<?> clazz) {
		final var desc = CLASS_DESCS.get(clazz);
		if (desc != null) {
			return desc;
		}
		return descCache.computeIfAbsent(clazz, BoilerplateGenerator::desc0);
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
