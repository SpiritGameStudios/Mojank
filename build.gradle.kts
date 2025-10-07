plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
	implementation(libs.bundles.implementation)
	compileOnly(libs.bundles.compileOnly)
	runtimeOnly(libs.bundles.runtimeOnly)

	testImplementation(platform("org.junit:junit-bom:5.10.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
