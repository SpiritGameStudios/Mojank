package dev.spiritstudios.mojank.meow.test.debug;

import dev.callmeecho.whisperer.DisassembleFlags;
import dev.callmeecho.whisperer.Disassembler;
import dev.spiritstudios.mojank.internal.Util;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleFileSaver;
import org.slf4j.Logger;

import java.lang.classfile.ClassFile;

public final class DebugUtils {
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
		var flags = new DisassembleFlags();
		flags.color = true;
		var disassembler = new Disassembler(flags);

		logger.info(disassembler.disassembleClass(ClassFile.of().parse(bytecode)));
	}
}
