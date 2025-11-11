package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.meow.Variables;
import dev.spiritstudios.mojank.meow.analysis.ClassType;
import dev.spiritstudios.mojank.meow.analysis.StructType;
import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.Opcode;
import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.DEFAULT_NAME;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;


/**
 * @author Ampflower
 **/
@ApiStatus.Internal
public final class BoilerplateGenerator {
	/**
	 * Generates a stub CompilerResult, this contains most important functions and class structure, excluding the main invoker.
	 */
	static void writeCompilerResultStub(
		final ClassDesc self,
		final Class<?> clazz,
		final Method target,
		final String source,
		final ClassBuilder builder
	) {
		var constPool = builder.constantPool();

		// will only throw if clazz is hidden, which it should never be
		var soup /* er */ = constPool.classEntry(clazz.describeConstable().orElseThrow());

		var owner = clazz.isInterface() ?
			CD_Object :
			soup.asSymbol();

		var compilerResult = constPool.classEntry(CompilerResult.DESCRIPTOR);

		builder
			.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);

		if (clazz.isInterface()) {
			builder.withInterfaces(
				soup,
				compilerResult
			);
		} else {
			builder
				.withSuperclass(soup)
				.withInterfaces(compilerResult);
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
							.instanceof_(compilerResult) // pop other, push bool
							.ifThenElse(
								Opcode.IFNE,
								eq -> eq
									.aload(1)
									.invokevirtual(
										CD_Object,
										"toString",
										MethodTypeDesc.ofDescriptor("()Ljava/lang/String;")
									)
									.ldc(constPool.stringEntry(source))
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
			cob ->
				cob.ldc(constPool.intEntry(source.hashCode()))
					.ireturn()
		);

		builder.withMethodBody(
			"getType",
			methodDesc(Class.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob ->
				cob.ldc(soup)
					.areturn()
		);

//		builder.withMethodBody(
//			"getCompiler",
//			methodDesc(Compiler.class),
//			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
//			cob -> cob.ldc(constPool.constantDynamicEntry(
//					DynamicConstantDesc.ofNamed(
//						MethodHandleDesc.ofMethod(
//							DirectMethodHandleDesc.Kind.STATIC,
//							MethodHandles.class.describeConstable().orElseThrow(),
//							"classData",
//							MethodTypeDesc.of(
//								ConstantDescs.CD_Object,
//								MethodHandles.Lookup.class.describeConstable().orElseThrow(),
//								ConstantDescs.CD_String,
//								ConstantDescs.CD_Class
//							)
//						),
//						ConstantDescs.DEFAULT_NAME,
//						desc(Compiler.class)
//					)
//				))
//				.areturn()
//		);

		builder.withMethodBody(
			"toHandle",
			methodDesc(MethodHandle.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob ->
				cob.ldc(constPool.methodHandleEntry(
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.VIRTUAL,
							self,
							target.getName(),
							methodDesc(
								target.getReturnType(),
								target.getParameterTypes()
							)
						)
					))
					.aload(0)
					.invokevirtual(
						desc(MethodHandle.class),
						"bindTo",
						methodDesc(MethodHandle.class, Object.class)
					)
					.areturn()
		);

		builder.withMethodBody(
			"createVariables",
			methodDesc(Variables.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.invokedynamic(DynamicCallSiteDesc.of(
					MethodHandleDesc.ofMethod(
						DirectMethodHandleDesc.Kind.STATIC,
						desc(MeowBootstraps.class),
						"constructor",
						methodDesc(
							CallSite.class,
							MethodHandles.Lookup.class,
							String.class,
							MethodType.class
						)
					),
					DEFAULT_NAME,
					methodDesc(Variables.class)
				))
				.areturn()
		);

		builder.withMethodBody(
			"toString",
			methodDesc(String.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(constPool.stringEntry(source))
				.areturn()
		);
	}

	private record Field(String name, MethodHandles.Lookup lookup) {}

	public static MethodHandles.Lookup writeVariablesClass(
		MethodHandles.Lookup lookup,
		final ClassDesc self,
		final StructType variables
	) throws IllegalAccessException {
		List<Field> fields = new ArrayList<>();

		byte[] bytecode = ClassFile.of().build(
			self,
			builder -> {
				builder.withInterfaces(builder.constantPool().classEntry(desc(Variables.class)));

				final StringBuilder nameBuilder = new StringBuilder();
				final ClassDesc[] descs = new ClassDesc[variables.members().size()];

				int descIndex = 0;
				for (final var entry : variables.members().entrySet()) {
					final var name = entry.getKey();
					final var type = entry.getValue();

					if (type instanceof ClassType classType) {
						var desc = desc(classType.clazz());

						builder.withField(name, desc, ClassFile.ACC_PUBLIC);

						nameBuilder.append(name).append(" = \u0001, ");
						descs[descIndex++] = desc;
					} else if (type instanceof StructType struct) {
						builder.withField(
							name,
							desc(Variables.class),
							ClassFile.ACC_PUBLIC
						);

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
					ClassFile.ACC_PRIVATE,
					cob -> {
						cob
							.aload(0) // push this
							.invokeInstruction(
								Opcode.INVOKESPECIAL,
								CD_Object,
								INIT_NAME,
								MTD_void,
								false
							);// call super

						for (int i = 0; i < fields.size(); i++) {
							var field = fields.get(i);

							cob
								.aload(0)
								.invokedynamic(
									DynamicCallSiteDesc.of(
										MeowBootstraps.CONSTRUCTOR_INDEXED,
										DEFAULT_NAME,
										methodDesc(Variables.class),
										i
									)
								)
								.putfield(
									self,
									field.name,
									desc(Variables.class)
								);
						}

						cob.return_();
					}
				);

				final String stringTemplate = nameBuilder.isEmpty() ?
					"\u0002 *empty*" :
					nameBuilder
						.insert(0, "\u0002 {")
						.replace(nameBuilder.length() - 2, nameBuilder.length(), "}")
						.toString();


//		builder.withMethodBody(
//			"toString",
//			methodDesc(String.class),
//			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
//			cob -> {
//				for (final var entry : variables.entrySet()) {
//					cob.aload(0).getfield(self, entry.getKey(), desc(entry.getValue()));
//				}
//
//				cob.invokedynamic(DynamicCallSiteDesc.of(
//						ofCallsiteBootstrap(
//							desc(StringConcatFactory.class),
//							"makeConcatWithConstants",
//							CD_CallSite,
//							CD_String,
//							desc(Object[].class)
//						),
//						"makeConcatWithConstants",
//						MethodTypeDesc.of(
//							CD_String,
//							descs
//						),
//						stringTemplate,
//						self
//					))
//					.areturn();
//			}
//		);
//
//
//		builder.withMethodBody(
//			"hashCode",
//			methodDesc(int.class),
//			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
//			variables.isEmpty() ? cob -> {
//				cob.ldc(self)
//					.invokevirtual(CD_Class, "hashCode", MethodTypeDesc.of(CD_int))
//					.ireturn();
//			} : cob -> {
//				int j = 0;
//				for (final var entry : variables.entrySet()) {
//					if (j != 0) {
//						cob.bipush(31).imul();
//					}
//
//					final var type = desc(entry.getValue());
//					cob.aload(0)
//						.getfield(self, entry.getKey(), type);
//
//					if (entry.getValue() == float.class) {
//						cob.invokestatic(CD_Float, "hashCode", MethodTypeDesc.of(CD_int, CD_float));
//					} else {
//						cob.invokevirtual(type, "hashCode", MethodTypeDesc.of(CD_int));
//					}
//
//					if (j++ != 0) {
//						cob.iadd();
//					}
//				}
//				cob.ireturn();
//			}
//		);
//
//		builder.withMethodBody(
//			"equals",
//			methodDesc(boolean.class, Object.class),
//			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
//			variables.isEmpty() ? cob -> cob.aload(1)
//				.ifThen(
//					Opcode.IFNONNULL,
//					ifNotNull -> ifNotNull.aload(1)
//						.invokevirtual(CD_Object, "getClass", MethodTypeDesc.of(CD_Class))
//						.ldc(self)
//						.ifThen(Opcode.IF_ACMPEQ, ifEq -> ifEq.iconst_1().ireturn())
//				).iconst_0().ireturn()
//				: cob -> cob
//				.aload(1)
//				.instanceof_(self)
//				.ifThen(ifInst -> {
//					// So we don't need to checkcast every time.
//					ifInst.aload(1).checkcast(self).astore(1);
//					final var itr = variables.entrySet().iterator();
//					while (itr.hasNext()) {
//						final var entry = itr.next();
//						final var field = ifInst.constantPool()
//							.fieldRefEntry(self, entry.getKey(), desc(entry.getValue()));
//
//						ifInst.aload(0)
//							.getfield(field)
//							.aload(1)
//							.getfield(field);
//
//						if (Primitives.primitiveLookup.containsKey(entry.getValue())) {
//							// Primitive special casing.
//							if (entry.getValue() == void.class) {
//								// Hello, who was the troll to put void in here?
//								throw new AssertionError(entry);
//							}
//							if (entry.getValue() == float.class) {
//								ifInst.fcmpg().ifne(ifInst.breakLabel());
//							} else if (entry.getValue() == double.class) {
//								ifInst.dcmpg().ifne(ifInst.breakLabel());
//							} else {
//								ifInst.if_icmpne(ifInst.breakLabel());
//							}
//						} else {
//							// For the sake of simplicitly, fork out to Objects.equals()
//							// Having to ifnonnull then call is annoying and costly in bytecode size.
//							ifInst.invokestatic(
//								desc(Objects.class),
//								"equals",
//								MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object)
//							);
//
//							// We can use this as a terminator if is no next entry.
//							if (itr.hasNext()) {
//								ifInst.ifne(ifInst.breakLabel());
//							} else {
//								ifInst.ireturn();
//								return; // Break out of the lambda
//							}
//						}
//					}
//					// We can finally return true.
//					ifInst.iconst_1().ireturn();
//				})
//				.iconst_0()
//				.ireturn()
//		);
			}
		);

		DebugUtils.decompile(bytecode);
//		DebugUtils.javap(bytecode);

		return lookup.defineHiddenClassWithClassData(
			bytecode,
			fields.stream().map(Field::lookup).toList(),
			true
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

	public static ClassDesc desc(Class<?> clazz) {
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
