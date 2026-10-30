import com.getcode.buildlogic.resources.androidResAsComposeResources

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.common.ui"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(project(":apps:flipcash:core"))
            implementation(project(":ui:core"))
            implementation(project(":ui:theme"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":apps:flipcash:shared:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material3)
            api(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.coil3)
        }
        androidMain.dependencies {
            implementation(project(":ui:components"))
            implementation(project(":ui:resources"))
            implementation(project(":libs:datetime"))
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.bundles.haze)
            implementation(libs.compose.material)
            api(libs.compose.material.icons.extended)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "${Gradle.flipcashNamespace}.shared.common.ui.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = androidResAsComposeResources(),
    )
}
