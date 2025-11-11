package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.ast.Expression;

import java.lang.invoke.MethodHandles;
import java.util.Map;

public record AnalysisResult(StructType variables, Map<Expression, StructType> locals, MethodHandles.Lookup variablesLookup) {

}
