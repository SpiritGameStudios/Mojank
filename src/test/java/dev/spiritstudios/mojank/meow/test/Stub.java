package dev.spiritstudios.mojank.meow.test;

import dev.spiritstudios.mojank.meow.Compiler;
import dev.spiritstudios.mojank.meow.CompilerResult;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Ampflower
 **/
final class Stub implements CompilerResult<Functor>, Functor {
	static final Functor self;

	static final Compiler<Functor> compiler;

	static final MethodHandle handle;

	static {
		try {
			final var lookup = MethodHandles.lookup();
			self = new Stub();
			compiler = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Compiler.class);
			handle = lookup
				.findVirtual(
					Stub.class,
					"invoke",
					MethodType.methodType(int.class, Context.class, Query.class, Variable.class)
				).bindTo(self);
		} catch (Throwable t) {
			throw new ExceptionInInitializerError(t);
		}
	}

	@Override
	public float invoke(final Context context, final Query query, final Variable variable) {
		// Injected
		return 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other/* || Stub.class == other.getClass()*/ ) {
			return true;
		}
		return other instanceof CompilerResult && "%INJECTED%".equals(other.toString());
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	@Override
	public Class<? extends Functor> getType() {
		return Stub.class;
	}

	@Override
	public Compiler<Functor> getCompiler() {
		return Stub.compiler;
	}

	@Override
	public MethodHandle toHandle() {
		return Stub.handle;
	}

	@Override
	public String toString() {
		return "%INJECTED%";
	}
}
