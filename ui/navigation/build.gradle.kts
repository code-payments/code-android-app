plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.navigation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":libs:parcelable"))
            implementation(project(":ui:core"))
            api(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.kotlin.reflect)
            implementation(libs.androidx.lifecycle.runtime.common)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.compose.view.models)
            api(libs.navigation3.runtime)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.material3)
            implementation(libs.compose.unstyled.bottom.sheet)
            implementation(libs.compose.activities)
            implementation(libs.bundles.hilt)
            api(libs.navigation3.ui)
            api(libs.lifecycle.viewmodel.navigation3)
            api(libs.hilt.nav.compose)
            api(libs.rinku)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.bundles.compose.ui.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
