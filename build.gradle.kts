plugins {
    `java-library`
	`maven-publish`
	idea
}

group = "dev.spiritstudios"
version = "1.0.0-SNAPSHOT"

base.archivesName = "mojank"


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
	implementation(libs.classfile)
	implementation(libs.fastutil)
	implementation(libs.slf4j)

	compileOnly(libs.annotations)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
	testCompileOnly(libs.annotations)

	testImplementation(libs.vineflower)
	testRuntimeOnly(libs.logback)

	testImplementation("com.roscopeco.jasm:jasm:0.7.0")
	testImplementation("org.ow2.asm:asm:9.9")

}

tasks.test {
    useJUnitPlatform()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
	repositories {
		mavenLocal()
		maven {
			name = "SpiritStudiosReleases"
			url = uri("https://maven.spritstudios.dev/snapshots")
			credentials(PasswordCredentials::class)
		}
	}
}
