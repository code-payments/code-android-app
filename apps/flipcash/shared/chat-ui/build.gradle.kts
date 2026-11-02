plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.chat.ui"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:core"))
            // api: ChatListItem.ContentBubble.capabilities exposes MessageCapability to consumers.
            api(project(":apps:flipcash:shared:chat"))
            implementation(project(":ui:core"))
            implementation(project(":ui:components"))
            implementation(project(":ui:theme"))
            implementation(project(":ui:resources"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode-compose"))
            implementation(project(":libs:datetime"))
            api(project(":apps:flipcash:shared:common-ui"))
            implementation(project(":apps:flipcash:shared:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.ui)
            // CodeTheme.shapes is Material 2's Shapes, so reading it needs the M2 artifact on the classpath.
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            // api: ComposerReplyStrip takes the host's HazeState so its card can sample the transcript.
            api(libs.bundles.haze)
            implementation(libs.androidx.paging.common)
            implementation(libs.compose.paging)
            implementation(libs.coil3)
        }
        androidMain.dependencies {
            // What flipcash.android.library.compose supplied before the move to KMP.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.androidx.paging.runtime)

            // CameraX for ChatCameraPanel, the same artifacts :ui:scanner uses.
            implementation(libs.androidx.camerax.core)
            implementation(libs.androidx.camerax.camera2)
            implementation(libs.androidx.camerax.lifecycle)
            implementation(libs.androidx.camerax.view)
            implementation(project(":libs:permissions:public"))
            implementation(libs.compose.activities)
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
        }
    }
}
