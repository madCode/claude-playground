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

// Replays a history file through the coach, for grading it in private (docs/REPLAY.md):
//   ./gradlew :coach:replay -Phistory=/path/history.jsonl -Pout=/path/outside/any/repo [-PperMovement]
tasks.register<JavaExec>("replay") {
    group = "verification"
    description = "Replays a history.jsonl through the coach and writes a scorecard session per class."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spotter.coach.ReplayKt"
    // Paths as typed, from the folder the command was run in. A cached configuration would keep
    // the folder of the first run and read, or write, somewhere else on the next.
    notCompatibleWithConfigurationCache("resolves its paths from the folder it is run in")
    val from = gradle.startParameter.currentDir
    args(
        listOf("history", "out").map { name -> (findProperty(name) as String?)?.let { from.resolve(it).path } ?: "" }.filter { it.isNotEmpty() } +
            // Grades a rule the app doesn't use yet (docs/REPLAY.md).
            listOfNotNull("per-movement".takeIf { project.hasProperty("perMovement") }),
    )
}
