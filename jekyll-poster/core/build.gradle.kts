plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    `java-test-fixtures`
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml.engine)
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    testFixturesImplementation(libs.okhttp.mockwebserver)
    testFixturesImplementation(libs.coroutines.core)
    testFixturesImplementation(libs.okhttp)
    testFixturesImplementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    systemProperty("sampleBlog", rootProject.file("sample-blog").path)
    // It writes to a real blog; only liveCheck runs it.
    exclude("**/LiveCheckTest*")
}

tasks.register<Test>("liveCheck") {
    description = "Publishes a post to -PliveRepo=owner/name through the real GitHub API, then deletes it."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*LiveCheckTest") }
    systemProperty("liveRepo", providers.gradleProperty("liveRepo").getOrElse(""))
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
