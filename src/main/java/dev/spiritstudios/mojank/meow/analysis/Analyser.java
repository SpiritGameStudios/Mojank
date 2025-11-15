package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.ast.AccessExpression;
import dev.spiritstudios.mojank.ast.ArrayAccessExpression;
import dev.spiritstudios.mojank.ast.BinaryOperationExpression;
import dev.spiritstudios.mojank.ast.ComplexExpression;
import dev.spiritstudios.mojank.ast.Expression;
import dev.spiritstudios.mojank.ast.FunctionCallExpression;
import dev.spiritstudios.mojank.ast.NumberExpression;
import dev.spiritstudios.mojank.ast.StringExpression;
import dev.spiritstudios.mojank.ast.TernaryOperationExpression;
import dev.spiritstudios.mojank.ast.UnaryOperationExpression;
import dev.spiritstudios.mojank.internal.Util;
import dev.spiritstudios.mojank.meow.compile.BoilerplateGenerator;
import dev.spiritstudios.mojank.meow.compile.IndexedParameter;
import dev.spiritstudios.mojank.meow.compile.Linker;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Analyser {
	private static final Logger logger = Util.logger();

	private final Map<Expression, StructType> locals = new Object2ObjectOpenHashMap<>();

	private final StructType variables = new StructType();

	private final Map<String, IndexedParameter> parameters;
	private final Linker linker;

	public Analyser(Map<String, IndexedParameter> parameters, Linker linker) {
		this.parameters = parameters;
		this.linker = linker;
	}

	public Type analyse(Expression expression) {
		var local = locals.computeIfAbsent(expression, k -> new StructType());
		var result = evalType(expression, local);

		return result;
	}

	public Type evalType(Expression expression, StructType locals) {
		return switch (expression) {
			case AccessExpression access -> {
				var first = access.first();

				if (Linker.isLocal(first)) {
					Type fieldType = locals;

					for (String field : access.fields()) {
						if (!(fieldType instanceof StructType struct)) {
							throw new IllegalStateException("Tried to access field of non-struct.");
						}

						fieldType = struct.members().computeIfAbsent(
							field,
							k -> new StructType(new Object2ObjectOpenHashMap<>())
						);
					}

					yield fieldType;
				} else if (Linker.isVariable(first)) {
					Type fieldType = variables;

					for (String field : access.fields()) {
						if (!(fieldType instanceof StructType struct)) {
							throw new IllegalStateException("Tried to access field of non-struct.");
						}

						fieldType = struct.members().computeIfAbsent(
							field,
							k -> new StructType()
						);
					}

					yield fieldType;
				}

				var param = parameters.get(first);

				Class<?> fieldType;
				String fieldName;

				if (param != null) {
					fieldType = param.parameter().getType();
				} else {
					fieldType = linker.findClass(first).orElseThrow();
				}

				List<String> fields = access.fields();
				for (String field : fields) {
					fieldName = field;
					var newField = linker.findField(fieldType, fieldName);

					fieldType = newField.getType();
				}

				yield ClassType.of(fieldType);
			}
			case ArrayAccessExpression arrayAccess -> {
				evalType(arrayAccess.index(), locals);

				yield ClassType.CT_Object; // TODO
			}
			case BinaryOperationExpression binary -> {
				var rightType = evalType(binary.right(), locals);

				if (binary.operator() == BinaryOperationExpression.Operator.SET) {
					if (binary.left() instanceof AccessExpression access) {
						if (Linker.isLocal(access.first())) {
							StructType type = locals;
							List<String> fields = access.fields();
							for (int i = 0; i < fields.size() - 1; i++) {
								String field = fields.get(i);
								var newType = type.members().computeIfAbsent(
									field,
									k -> new StructType()
								);

								if (!(newType instanceof StructType struct)) {
									throw new UnsupportedOperationException("what");
								}

								type = struct;
							}

							type.members().put(access.fields().getLast(), rightType);
						} else if (Linker.isVariable(access.first())) {
							StructType type = variables;
							List<String> fields = access.fields();
							for (int i = 0; i < fields.size() - 1; i++) {
								String field = fields.get(i);
								var newType = type.members().computeIfAbsent(
									field,
									k -> new StructType()
								);

								if (!(newType instanceof StructType struct)) {
									throw new UnsupportedOperationException("what");
								}

								type = struct;
							}

							type.members().compute(access.fields().getLast(), (k, existing) -> {
								if (existing == null) return rightType;
								if (existing instanceof UnionType union) {
									union.types().add(rightType);
									return union;
								}

								return new UnionType(existing, rightType);
							});
						}
					}
				}

				yield rightType;
			}
			case FunctionCallExpression function -> {
				for (Expression argument : function.arguments()) {
					evalType(argument, locals);
				}

				if (!(function.function() instanceof AccessExpression access)) {
					throw new UnsupportedOperationException("Function must have an access expression on the left.");
				}

				if (access.first().equals("loop")) {
					yield ClassType.CT_void;
				}

				var method = linker.findMethod(access);

				yield ClassType.of(method.getReturnType());
			}
			case NumberExpression ignored -> ClassType.CT_float;
			case StringExpression ignored -> ClassType.CT_String;
			case TernaryOperationExpression ternary -> {
				var falseType = evalType(ternary.ifFalse(), locals);
				var trueType = evalType(ternary.ifTrue(), locals);

				// FIXME: Different types on each side of the ternary
				if (!Objects.equals(falseType, trueType))
					throw new UnsupportedOperationException("Ternary operators must return the same type for both true and false.");

				yield falseType;
			}
			case UnaryOperationExpression unary -> evalType(unary.value(), locals);
			case ComplexExpression complex -> {
				for (Expression sub : complex.expressions()) {
					evalType(sub, locals);
				}

				yield ClassType.CT_Object;
			}
			default -> ClassType.CT_Object;
		};
	}

	@VisibleForTesting
	public byte[] createVariables(MethodHandles.Lookup lookup) {
		return BoilerplateGenerator.compileVariables(
			lookup,
			ClassDesc.of(lookup.lookupClass().getPackage().getName(), "Variables"),
			variables,
			new ArrayList<>()
		);
	}

	public AnalysisResult finish(MethodHandles.Lookup lookup) throws IllegalAccessException {
		var variablesLookup = variables.members().isEmpty() ? null : BoilerplateGenerator.writeVariablesClass(
			lookup,
			ClassDesc.of(lookup.lookupClass().getPackage().getName(), "Variables"),
			variables
		);

		return new AnalysisResult(
			variables,
			locals,
			variablesLookup
		);
	}
}
