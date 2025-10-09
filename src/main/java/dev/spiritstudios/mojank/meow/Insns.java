package dev.spiritstudios.mojank.meow;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * @author Ampflower
 **/
final class Insns extends MethodVisitor {
	private final InsnList insns;
	private int maxStack = -1, maxLocals = -1;

	public Insns() {
		this(new InsnList());
	}

	public Insns(final InsnList insns) {
		super(Opcodes.ASM9);
		this.insns = insns;
	}

	public Insns(final MethodVisitor parent) {
		this(parent, new InsnList());
	}

	public Insns(final MethodVisitor parent, final InsnList insns) {
		super(Opcodes.ASM9, parent);
		this.insns = insns;
	}

	@Override
	public void visitFrame(
		final int type,
		final int numLocal,
		final Object[] local,
		final int numStack,
		final Object[] stack
	) {
		this.insns.insert(new FrameNode(type, numLocal, local, numStack, stack));
	}

	@Override
	public void visitInsn(final int opcode) {
		this.insns.insert(new InsnNode(opcode));
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		this.insns.insert(new IntInsnNode(opcode, operand));
	}

	@Override
	public void visitVarInsn(final int opcode, final int varIndex) {
		this.insns.insert(new VarInsnNode(opcode, varIndex));
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
		super.visitFieldInsn(opcode, owner, name, descriptor);
	}

	@Override
	public void visitMethodInsn(
		final int opcode,
		final String owner,
		final String name,
		final String descriptor,
		final boolean isInterface
	) {
		super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
	}

	@Override
	public void visitInvokeDynamicInsn(
		final String name,
		final String descriptor,
		final Handle bootstrapMethodHandle,
		final Object... bootstrapMethodArguments
	) {
		super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
	}

	@Override
	public void visitJumpInsn(final int opcode, final Label label) {
		super.visitJumpInsn(opcode, label);
	}

	@Override
	public void visitLabel(final Label label) {
		super.visitLabel(label);
	}

	@Override
	public void visitLdcInsn(final Object value) {
		super.visitLdcInsn(value);
	}

	@Override
	public void visitIincInsn(final int varIndex, final int increment) {
		super.visitIincInsn(varIndex, increment);
	}

	@Override
	public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label... labels) {
		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override
	public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
		super.visitMultiANewArrayInsn(descriptor, numDimensions);
	}

	@Override
	public void visitLineNumber(final int line, final Label start) {
		this.insns.insert(new LineNumberNode(line, new LabelNode(start)));
	}

	@Override
	public void visitMaxs(final int maxStack, final int maxLocals) {
		this.maxStack = maxStack;
		this.maxLocals = maxLocals;
	}

	@Override
	public void visitEnd() {
		//no-op
	}

	// === Internal Utilities ===

	private static LabelNode[] toNodes(final Label... labels) {
		final var array = new LabelNode[labels.length];
		for (int i = 0; i < labels.length; i++) {
			array[i] = new LabelNode(labels[i]);
		}
		return array;
	}

	// === Unsupported Operations - do not call ===

	@Override
	@Deprecated
	public MethodVisitor getDelegate() {
		return null;
	}

	@Override
	@Deprecated
	public void visitParameter(final String name, final int access) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public AnnotationVisitor visitAnnotationDefault() {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public AnnotationVisitor visitTypeAnnotation(
		final int typeRef,
		final TypePath typePath,
		final String descriptor,
		final boolean visible
	) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public AnnotationVisitor visitParameterAnnotation(
		final int parameter,
		final String descriptor,
		final boolean visible
	) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void visitAttribute(final Attribute attribute) {
		throw new UnsupportedOperationException();
	}
}
