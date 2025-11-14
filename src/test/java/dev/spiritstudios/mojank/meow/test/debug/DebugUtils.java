package dev.spiritstudios.mojank.meow.test.debug;

import com.roscopeco.jasm.JasmDisassemblingVisitor;
import com.roscopeco.jasm.errors.StandardErrorCollector;
import dev.spiritstudios.mojank.internal.Util;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;

public class DebugUtils {
	private static final Logger logger = Util.logger();

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
}
