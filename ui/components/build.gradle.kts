import com.getcode.buildlogic.resources.androidResAsComposeResources

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    android {
        namespace = "com.getcode.ui.components"
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.material)
            api(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.material.icons.extended)
            api(libs.jetbrains.compose.ui.tooling.preview)
            // api: TopScrollEdge and the app bar take a HazeState, so callers need the type.
            api(libs.bundles.haze)
            api(project(":ui:core"))
            implementation(project(":ui:theme"))
            implementation(project(":ui:resources"))
            implementation(project(":libs:network:connectivity:public"))
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(project(":libs:datetime"))
            implementation(project(":libs:encryption:ed25519"))
            implementation(project(":libs:encryption:utils"))
            implementation(project(":libs:currency"))
            implementation(project(":libs:messaging"))
            implementation(project(":libs:models"))
            implementation(project(":libs:vibrator:public"))
            implementation(project(":ui:navigation"))
            implementation(libs.coil3)
            implementation(libs.coil3.network)
            implementation(libs.kotlinx.datetime)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui.text)
            implementation(libs.compose.activities)
            implementation(libs.compose.material)
            api(libs.compose.material3)
            api(libs.compose.ui.tools.preview)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.accompanist)
            implementation(libs.compose.paging)
            api(libs.vico.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.bundles.compose.ui.testing)
        }
    }
}


compose.resources {
    publicResClass = true
    packageOfResClass = "${Gradle.codeNamespace}.ui.components.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = androidResAsComposeResources(),
    )
}
