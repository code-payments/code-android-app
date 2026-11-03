plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.features.chatlist"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:shared:chat"))
            implementation(project(":apps:flipcash:shared:chat-ui"))
            implementation(project(":apps:flipcash:shared:common-ui"))
            implementation(project(":apps:flipcash:shared:theme"))
            implementation(project(":apps:flipcash:shared:tokens"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:coroutines"))
            implementation(project(":ui:components"))
            implementation(project(":ui:core"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.bundles.haze)
            implementation(libs.javax.inject)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(project(":ui:resources:test-fixtures"))
            implementation(project(":libs:test-utils"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
