import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.js)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: ErrorUtils and the rest that needs java.* or gRPC, for Android and the desktop JVM.
    // commonMain holds trace() and stays free of both, so JS can call it.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    android {
        namespace = "${Gradle.codeNamespace}.libs.logging"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.annotation)
        }
        getByName("jvmSharedMain").dependencies {
            implementation(libs.grpc.kotlin)
            implementation(project(":libs:messaging"))
        }
        androidMain.dependencies {
            api(libs.timber)
            // Contract versions for the log header in FileTree.
            implementation(libs.ocp.client.protocol)
            implementation(libs.flipcash2.client.protocol)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
        }
    }
}
