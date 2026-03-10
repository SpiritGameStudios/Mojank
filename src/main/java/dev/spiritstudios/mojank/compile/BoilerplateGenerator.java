package dev.spiritstudios.mojank.compile;

import org.jetbrains.annotations.ApiStatus;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.constant.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;

import static dev.spiritstudios.mojank.compile.Descriptors.*;
import static java.lang.constant.ConstantDescs.*;

/**
 * @author Ampflower
 **/
@ApiStatus.Internal
public final class BoilerplateGenerator {
	public static void tryCast(Class<?> from, Class<?> to, CodeBuilder builder) {
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
		}
		if (out == int.class) {
			return (int) in;
		}
		if (out == double.class) {
			return (double) in;
		}
		if (out == boolean.class) {
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
							.instanceOf(desc(CompilerResult.class)) // pop other, push bool
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

	public static String loopIndexName(int depth) {
		return String.valueOf((char) ((int) 'i' + depth)); // If you have a loop nested over 17 layers deep then that's your fault.
	}

	public static void generateConstructor(ClassBuilder builder, ClassDesc owner) {
		builder.withMethodBody(
			INIT_NAME,
			MTD_void,
			ClassFile.ACC_PRIVATE,
			cob -> cob
				.aload(0) // push this
				.invoke(
					Opcode.INVOKESPECIAL,
					owner,
					INIT_NAME,
					MTD_void,
					false
				)
				.return_()
		);
	}

	public static void wrapArrayIndex(CodeBuilder builder) {
		builder
			.iconst_0()
			.invokestatic(desc(Math.class), "max", MethodTypeDesc.of(CD_int, CD_int, CD_int))
			// TODO: We could reorder this so that the array is loaded at this point,
			//  then just dup_x1 it behind the int as well.
			//  Although this whole operation is frankly nonsensical and needs more thought.
			.swap()
			.dup_x1()
			.arraylength()
			.irem();
	}
}
