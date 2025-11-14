package dev.spiritstudios.mojank.meow.test.debug;

import com.roscopeco.jasm.JasmDisassemblingVisitor;
import com.roscopeco.jasm.errors.StandardErrorCollector;
import dev.spiritstudios.mojank.internal.Util;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DebugUtils {
	private static final Logger logger = Util.logger();

	private static final MethodHandle javap;

	// Enjoy :3
	static {
		MethodHandle jp = MethodHandles.empty(MethodType.methodType(void.class, String[].class));
		try {
			final var lookup = MethodHandles.lookup();

			final var clazz = Class.forName("com.sun.tools.javap.Main");
			final var method = clazz.getMethod("run", String[].class, PrintWriter.class);

			method.setAccessible(true);

			jp = MethodHandles.insertArguments(lookup.unreflect(method), 1, new PrintWriter(System.out) {
				@Override
				public void close() {
					// no-op
				}
			}).asVarargsCollector(String[].class);
		} catch (Throwable t) {
			logger.warn("Unable to load javap:", t);
		}
		javap = jp;
	}

	public static void debug(byte[] bytecode) {
		DebugUtils.decompile(bytecode);
		DebugUtils.disassemble(bytecode);
	}

	private static void decompile(byte[] bytecode) {
		Decompiler decompiler = Decompiler.builder()
			.inputs(new ByteArrayContextSource("", bytecode))
			.output(new ConsoleFileSaver(null))
			.build();

		decompiler.decompile();
	}

	private static void disassemble(byte[] bytecode) {
		var disassembler = new JasmDisassemblingVisitor("Functor", new StandardErrorCollector());
		var reader = new ClassReader(bytecode);

		reader.accept(disassembler, ClassReader.SKIP_FRAMES);

		logger.info(disassembler.output());
	}


	private static void disassembleWithJavap(byte[] bytecode) {
		Path temp = null;
		try {
			temp = Files.createTempFile("meow", ".class");

			Files.write(temp, bytecode);

			javap.invoke("-l", "-p", "-c", temp.normalize().toString());
		} catch (Throwable t) {
			logger.warn("Unable to execute javap: ", t);
		} finally {
			try {
				if (temp != null) {
					Files.delete(temp);
				}
			} catch (IOException ignored) {
			}
		}
	}
}
