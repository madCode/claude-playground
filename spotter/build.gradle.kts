plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

dependencies {
    kover(project(":app"))
    kover(project(":coach"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code: Compose's, kotlinx.serialization's and Room's.
                classes("*ComposableSingletons*", "*\$\$serializer", "*_Impl", "*_Impl\$*")
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
