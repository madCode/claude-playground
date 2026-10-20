plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciCommit = System.getenv("GITHUB_SHA")?.take(7)

android {
    namespace = "com.app.jekyllposter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.jekyllposter"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.app.jekyllposter.device.DeviceTestRunner"
        // Each device test in a process of its own, with the app's data cleared: the app's
        // DataStores are process-wide, and a sign-in would carry into the next test.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // Files a test keeps (the live check's request record) are pulled into the build's outputs.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        // A GitHub App's client ID turns on "Sign in with GitHub" (device flow). Not a secret: the
        // device flow needs no client secret. Empty hides the button and leaves token sign-in.
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${providers.gradleProperty("githubClientId").getOrElse("")}\"")
        // The GitHub App's URL name, for the "install it on your blog" link.
        buildConfigField("String", "GITHUB_APP_SLUG", "\"${providers.gradleProperty("githubAppSlug").getOrElse("")}\"")
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
        buildConfig = true
    }

    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Robolectric's Android 16+ runtime needs this on JDK 21.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                it.systemProperty("sampleBlog", rootProject.file("sample-blog").path)
                it.systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
                // Design rounds (DesignRoundTest) only render with -Pdesign; otherwise they're skipped.
                if (providers.gradleProperty("design").isPresent) {
                    it.systemProperty("designDir", layout.buildDirectory.dir("design").get().asFile.path)
                }
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Compose UI test pulls an older Espresso that crashes on API 37.
    testImplementation(libs.espresso.core)

    androidTestImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.androidx.test.storage)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}

// The sample blog, zipped into the device tests' assets: the fake GitHub on the device serves it.
// Zipped, because the asset packager leaves out folders starting with "_", like _posts.
val sampleBlogAssets = layout.buildDirectory.dir("generated/sampleBlogAssets").get().asFile
val sampleBlogZip = tasks.register<Zip>("sampleBlogZip") {
    from(rootProject.file("sample-blog"))
    archiveFileName.set("sample-blog.zip")
    destinationDirectory.set(sampleBlogAssets)
}
android.sourceSets.getByName("androidTest").assets.srcDir(sampleBlogAssets)
// Every task of the device tests reads the folder (assets, lint models), not only the merge.
tasks.named { it.contains("AndroidTest") }.configureEach { dependsOn(sampleBlogZip) }


androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        ciRun?.let { run -> variant.outputs.forEach { it.versionCode.set(run) } }
    }
}
