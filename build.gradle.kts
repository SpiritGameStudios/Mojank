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
}

tasks.test {
    useJUnitPlatform()
}
