plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":core"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code: Room's DAOs and Compose's singletons.
                classes("*_Impl", "*_Impl\$*", "*ComposableSingletons*")
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
