plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":coach"))
}

kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
