plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciCommit = System.getenv("GITHUB_SHA")?.take(7)

android {
    namespace = "com.app.bartwidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.bartwidget"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        ciRun?.let { run -> variant.outputs.forEach { it.versionCode.set(run) } }
    }
}
