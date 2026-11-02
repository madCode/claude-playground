plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":brain"))
}

kover {
    reports {
        filters {
            excludes {
                classes("*ComposableSingletons*", "*\$\$serializer")
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
