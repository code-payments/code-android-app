plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.core.ui"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(project(":apps:flipcash:core"))
            implementation(project(":services:opencode"))
            implementation(project(":services:opencode-compose"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":ui:components"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))
            implementation(project(":apps:flipcash:shared:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.coil3)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.rinku.compose)
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":ui:navigation"))
            implementation(project(":apps:flipcash:shared:featureflags"))

            implementation(libs.compose.material3)
            implementation(libs.bundles.haze)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
