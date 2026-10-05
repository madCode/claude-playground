plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated: Compose's singletons and the serializers for the stored boards.
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
