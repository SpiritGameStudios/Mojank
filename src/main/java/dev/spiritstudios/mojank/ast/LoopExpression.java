package dev.spiritstudios.mojank.ast;

import dev.spiritstudios.mojank.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.compile.CompileContext;
import dev.spiritstudios.mojank.compile.Loop;
import dev.spiritstudios.mojank.compile.Primitive;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import static java.lang.constant.ConstantDescs.CD_int;

public record LoopExpression(Expression count, Expression body) implements Expression {
	@Override
	public Class<?> type(CompileContext context) {
		return void.class;
	}

	@Override
	public Class<?> emit(CompileContext context, CodeBuilder builder) {
		builder.block(b -> {
			int indexSlot = b.allocateLocal(TypeKind.INT);
			b.localVariable(
					indexSlot,
					BoilerplateGenerator.loopIndexName(context.loops().size()),
					CD_int,
					b.startLabel(),
					b.endLabel()
				)
				.iconst_0()
				.istore(indexSlot);

			var continue_ = b.newLabel();

			var start = b.newBoundLabel();

			b.iload(indexSlot);

			var countType = count.emit(context, b);
			BoilerplateGenerator.tryCast(countType, int.class, b);

			b.if_icmpge(b.breakLabel());

			context.loops().push(new Loop(
				continue_,
				b.breakLabel()
			));

			body.emit(context, b);

			context.loops().pop();

			b
				.labelBinding(continue_)
				.iinc(indexSlot, 1)
				.goto_(start);
		});

		return void.class;
	}
}
