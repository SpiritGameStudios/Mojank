package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.CompileContext;
import java.lang.classfile.CodeBuilder;

public enum KeywordExpression implements Expression {
	BREAK,
	CONTINUE;

	@Override
	public Class<?> type(CompileContext context) {
		return void.class;
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		switch (this) {
			case BREAK -> {
				var loop = context.loops().peek();
				if (loop == null) {
					throw new IllegalStateException("Tried to break when not inside a loop!");
				}
				builder.goto_(loop.break_());
			}
			case CONTINUE -> {
				var loop = context.loops().peek();
				if (loop == null) {
					throw new IllegalStateException("Tried to continue when not inside a loop!");
				}
				builder.goto_(loop.continue_());
			}
		}

		return void.class;
	}
}
