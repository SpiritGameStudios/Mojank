package dev.spiritstudios.mojank.meow;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class ByteArrayContextSource implements IContextSource {
	private final String qualifiedName;
	private final byte[] contents;

	public ByteArrayContextSource(String qualifiedName, byte[] contents) {
		this.qualifiedName = qualifiedName;
		this.contents = contents;
	}

	@Override
	public String getName() {
		return qualifiedName;
	}

	@Override
	public Entries getEntries() {
		return new Entries(List.of(Entry.atBase(this.qualifiedName)), List.of(), List.of());
	}

	@Override
	public InputStream getInputStream(String resource) {
		return new ByteArrayInputStream(this.contents);
	}

	@Override
	public IOutputSink createOutputSink(IResultSaver saver) {
		return new IOutputSink() {
			@Override
			public void close() {
			}

			@Override
			public void begin() {
			}

			@Override
			public void acceptOther(String path) {

			}

			@Override
			public void acceptDirectory(String directory) {
				// not used
			}

			@Override
			public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
				String entryName = fileName.substring(fileName.lastIndexOf('/') + 1);
				saver.saveClassFile("", qualifiedName, entryName, content, mapping);
			}
		};
	}


}
