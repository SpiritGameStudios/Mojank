plugins {
    `java-library`
	idea
}

idea {
	module {
		isDownloadSources = true
		isDownloadJavadoc = true
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
    mavenCentral()
}

dependencies {
	implementation(libs.bundles.implementation)
	compileOnly(libs.bundles.compileOnly)
	runtimeOnly(libs.bundles.runtimeOnly)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
	testCompileOnly(libs.annotations)

	testImplementation(libs.vineflower)

	testImplementation("com.roscopeco.jasm:jasm:0.7.0")
	testImplementation("org.ow2.asm:asm:9.9")

}

tasks.test {
    useJUnitPlatform()
}
