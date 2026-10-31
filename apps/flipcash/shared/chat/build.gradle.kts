plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.chat"
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: GroupAccess.groupAccess is an extension on TokenCoordinator,
            // so this module's public surface names a type from it.
            api(project(":apps:flipcash:shared:tokens"))
            // `api`: ChatMediaUploads.stage takes the blob module's PickedPhoto.
            api(project(":apps:flipcash:shared:blob"))
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:shared:persistence:sources"))
            implementation(project(":apps:flipcash:shared:persistence:db"))
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":libs:network:connectivity:public"))
            implementation(project(":libs:emojis"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.bundles.hilt.common)
            implementation(libs.coil3.core)
            implementation(libs.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.bundles.hilt)
            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.lifecycle.process)
        }
        // reactions.json is read from the repo's canonical test-vectors/ rather than copied in.
        commonTest {
            resources.srcDir(rootProject.file("test-vectors"))
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(libs.androidx.paging.testing)
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
            implementation(project(":services:flipcash:test-fixtures"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
