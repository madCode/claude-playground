// A second app, so the device tests can share a photo and a note into Jekyll Poster from another
// process, with a real URI grant, as Photos and Obsidian do. Never shipped: only the device-test
// jobs install it.
plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.app.jekyllposter.sender"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.app.jekyllposter.sender"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
}
