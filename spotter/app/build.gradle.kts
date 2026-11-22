plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciCommit = System.getenv("GITHUB_SHA")?.take(7)

android {
    namespace = "spotter.app"
    compileSdk = 37

    defaultConfig {
        // Not Carry's package, so both install side by side until the scorecard decides.
        applicationId = "com.app.spotter"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // One debug key for every machine, so a newer CI build installs over an older one
        // instead of failing on a signature mismatch. Not a secret: it signs debug builds only.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug" + (ciRun?.let { ".$it" } ?: "") + (ciCommit?.let { "+$it" } ?: "")
            // Only a build CI published can find a newer one: a local build has no number to
            // compare, and replacing it with main's build would throw away what is being tried.
            buildConfigField("int", "CI_BUILD", (ciRun ?: 0).toString())
        }
        release {
            isMinifyEnabled = false
            buildConfigField("int", "CI_BUILD", "0")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                // One JVM per class: in a shared one, captureToImage can return blank bitmaps
                // and the screenshots pass while showing nothing.
                it.setForkEvery(1L)
                it.systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
            }
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// A CI debug build's version code is its run number, so each build from main installs over the
// one before and the app can tell which is newer. Local builds keep 1.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        ciRun?.let { run -> variant.outputs.forEach { it.versionCode.set(run) } }
    }
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<SelfUpdateManifestCheck>("check${name}SelfUpdateManifest") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            selfUpdates.set(variant.buildType == "debug")
        }
        tasks.named("check") { dependsOn(check) }
    }
}

/**
 * Fails when a variant's merged manifest disagrees about updating itself: the debug build installs
 * newer CI builds (src/debug); a release must not be able to install apps. Both reach the network,
 * for the day's board.
 * Without UPDATE_PACKAGES_WITHOUT_USER_ACTION every install waits for a tap that a background
 * install never shows, so the phone silently stops updating.
 */
abstract class SelfUpdateManifestCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val selfUpdates: Property<Boolean>

    @TaskAction
    fun check() {
        val text = manifest.get().asFile.readText()
        if ("android.permission.INTERNET" !in text) throw GradleException("${manifest.get().asFile}: INTERNET should be there, for the day's board")
        for (name in listOf(
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
            "spotter.app.update.InstallResult",
        )) {
            if ((name in text) != selfUpdates.get()) {
                throw GradleException("${manifest.get().asFile}: $name should " + if (selfUpdates.get()) "be there" else "not be there")
            }
        }
    }
}

// Each version of the log's schema is kept in the repository, so a change to it is reviewed and
// a migration can be tested against the exact schema a phone has.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":coach"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.work.runtime)

    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    // Compose tests on Robolectric inject input through Espresso; an older one looks for an
    // InputManager method newer Android removed.
    testImplementation(libs.espresso.core)
}
