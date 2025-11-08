package dev.spiritstudios.mojank.meow.analysis;

import dev.spiritstudios.mojank.ast.Expression;

import java.util.Map;

public record AnalysisResult(StructType variables, Map<Expression, StructType> locals) {
}
