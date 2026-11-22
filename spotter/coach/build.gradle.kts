// The coach: plain Kotlin with no Android code, so every rule runs in JVM tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}

// ScorecardTest compares the export with this file, so a change to it reruns the tests.
tasks.test {
    inputs.file("../docs/scorecard-example.json")
}
