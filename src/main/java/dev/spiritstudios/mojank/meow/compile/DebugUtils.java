package dev.spiritstudios.mojank.meow.compile;

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;

import java.io.PrintWriter;
import java.nio.file.Files;

public class DebugUtils {
	public static void decompile(byte[] bytecode) {
		Decompiler decompiler = Decompiler.builder()
			.inputs(new ByteArrayContextSource("", bytecode))
			.output(new ConsoleFileSaver(null))
			.build();

		decompiler.decompile();
	}

	public static void javap(byte[] bytecode) {
		try {
			final var clazz = Class.forName("com.sun.tools.javap.Main");
			final var method = clazz.getMethod("run", String[].class, PrintWriter.class);

			method.setAccessible(true);

			final var temp = Files.createTempFile("meow", ".class");

			Files.write(temp, bytecode);

			method.invoke(
				null,
				new String[] {"-v", "-p", "-c", temp.normalize().toString()},
				new PrintWriter(System.out) {
					@Override
					public void close() {
						// no-op
					}
				}
			);

			Files.delete(temp);
		} catch (Exception roe) {
			// Frankly, it doesn't matter; it's only for debugging anyway.
		}
	}
}
