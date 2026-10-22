plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciCommit = System.getenv("GITHUB_SHA")?.take(7)

android {
    namespace = "com.app.carry"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.carry"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each device test in a process of its own, with the app's data cleared, so one test's
        // folder grant and record of seen sheets don't carry into the next.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // A failed test's screen and window hierarchy are pulled into the build's outputs.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    signingConfigs {
        // One debug key for every machine, so a newer debug build from CI installs over an
        // older one instead of failing on a signature mismatch. Not a secret: debug-only.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        // So the app can show which build it is -- see SinceScreen.
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug" + (ciRun?.let { ".$it" } ?: "") + (ciCommit?.let { "+$it" } ?: "")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Robolectric's Android 16+ runtime needs this on JDK 21.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                // One JVM per class. Without it, running the whole suite in
                // a single JVM left SEVEN of the twelve screenshots as the
                // same blank image -- captureToImage returning an empty
                // bitmap rather than failing, so the tests stayed green and
                // CI uploaded the blanks as evidence. Two of them fooled me
                // during this project. Costs a few seconds; buys pictures
                // that are worth looking at.
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
// one before and a phone can tell which is newer. Local builds keep 1.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        ciRun?.let { run -> variant.outputs.forEach { it.versionCode.set(run) } }
    }
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val manifestCheck = tasks.register<SelfUpdateManifestCheck>("check${name}SelfUpdateManifest") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            selfUpdates.set(variant.buildType == "debug")
        }
        tasks.named("check") { dependsOn(manifestCheck) }
    }
}

/**
 * Fails when a variant's merged manifest disagrees about updating itself: the debug build installs
 * newer CI builds (src/debug), and a release must not be able to install apps or reach the network.
 * Checking debug as well keeps the release check from passing on a manifest it misread. Neither
 * may ask for FOREGROUND_SERVICE, which WorkManager brings and Carry has no use for.
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
        for (name in listOf("android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.INTERNET", "UpdateInstaller\$Result")) {
            if ((name in text) != selfUpdates.get()) {
                throw GradleException("${manifest.get().asFile}: $name should " + if (selfUpdates.get()) "be there" else "not be there")
            }
        }
        // Removed from WorkManager's in src/main: Carry runs no foreground work (docs/DESIGN.md).
        if ("\"android.permission.FOREGROUND_SERVICE\"" in text) {
            throw GradleException("${manifest.get().asFile}: android.permission.FOREGROUND_SERVICE should not be there")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
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
    testImplementation(libs.espresso.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.storage)
    androidTestImplementation(libs.uiautomator)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
