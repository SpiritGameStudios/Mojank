package dev.spiritstudios.mojank.meow.compile;

import org.glavo.classfile.Label;

public record Loop(Label continue_, Label break_) {
}
