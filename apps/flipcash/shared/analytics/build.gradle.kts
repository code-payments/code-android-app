plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.anaylytics"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:analytics-events"))
            implementation(libs.jetbrains.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":libs:logging"))
            implementation(project(":libs:currency-math"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(libs.androidx.core)
            implementation(libs.mixpanel)
            implementation(libs.bundles.hilt)
        }
        getByName("androidHostTest").dependencies {
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
}
