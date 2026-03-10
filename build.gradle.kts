plugins {
    `java-library`
	`maven-publish`
	idea
}

group = "dev.spiritstudios"
version = "1.0.0-SNAPSHOT2"
base.archivesName = "mojank"


idea {
	module {
		isDownloadSources = true
		isDownloadJavadoc = true
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
    mavenCentral()
	mavenLocal()
}

dependencies {
	implementation(libs.fastutil)
	implementation(libs.slf4j)

	compileOnly(libs.annotations)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
	testCompileOnly(libs.annotations)

	testImplementation(libs.vineflower)
	testImplementation(libs.whisperer)
	testRuntimeOnly(libs.logback)
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
