plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin { jvmToolchain(17) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.networknt:json-schema-validator:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    maxHeapSize = "1536m"
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = false }
}

tasks.register<JavaExec>("generateExample") {
    description = "Writes a synthetic example export to docs/example/session-example"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.lumisite.core.ExampleGeneratorKt")
    args(rootProject.file("docs/example/session-example").absolutePath)
}
