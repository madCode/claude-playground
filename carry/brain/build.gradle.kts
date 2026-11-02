// The coach, ported from gym-coach's Python: plain Kotlin, no Android, so its
// tests run on the JVM in seconds and a cloud session can run them.
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

tasks.test {
    // The fixtures gym-coach writes, checked in beside this module.
    systemProperty("brainFixtures", rootProject.file("brain-fixtures").absolutePath)
}
