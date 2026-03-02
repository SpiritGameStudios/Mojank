package dev.spiritstudios.mojank.compile;

import java.lang.classfile.Label;

public record Loop(Label continue_, Label break_) {
}
