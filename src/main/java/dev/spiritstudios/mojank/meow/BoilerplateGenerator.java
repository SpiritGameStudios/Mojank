package dev.spiritstudios.mojank.meow;

import org.glavo.classfile.AccessFlag;
import org.glavo.classfile.ClassBuilder;
import org.glavo.classfile.ClassFile;
import org.glavo.classfile.Opcode;
import org.glavo.classfile.constantpool.ConstantPoolBuilder;
import org.jetbrains.annotations.ApiStatus;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;


/**
 * @author Ampflower
 **/
@ApiStatus.Internal
public final class BoilerplateGenerator {
	/**
	 * Generates a stub CompilerResult, this contains most important functions and class structure, excluding the main invoker.
	 */
	static void writeStub(
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
			ConstantDescs.CD_Object :
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
										ConstantDescs.CD_Object,
										"toString",
										MethodTypeDesc.ofDescriptor("()Ljava/lang/String;")
									)
									.ldc(constPool.stringEntry(source))
									.invokevirtual(
										ConstantDescs.CD_String,
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
			"toString",
			methodDesc(String.class),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
			cob -> cob
				.ldc(constPool.stringEntry(source))
				.areturn()
		);
	}

	public static void generateConstructor(ClassBuilder builder, ClassDesc owner) {
		builder.withMethodBody(
			ConstantDescs.INIT_NAME,
			ConstantDescs.MTD_void,
			ClassFile.ACC_PRIVATE,
			cob -> cob
				.aload(0) // push this
				.invokeInstruction(
					Opcode.INVOKESPECIAL,
					owner,
					ConstantDescs.INIT_NAME,
					ConstantDescs.MTD_void,
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
