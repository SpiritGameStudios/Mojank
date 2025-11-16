package dev.spiritstudios.mojank.meow.compile;

import dev.spiritstudios.mojank.meow.analysis.StructType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.Deque;

public record CompileContext(
	StructType localsType,
	Object2IntMap<String> locals,
	Deque<Loop> loops
) {
	public CompileContext(StructType localsType) {
		this(
			localsType,
			new Object2IntOpenHashMap<>(localsType.members().size()),
			new ArrayDeque<>()
		);
	}
}
