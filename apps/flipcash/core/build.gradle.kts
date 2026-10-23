import com.getcode.buildlogic.resources.androidResAsComposeResources

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.core"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:parcelable"))
            implementation(libs.javax.inject)
            api(libs.androidx.lifecycle.runtime.common)
            api(project(":ui:navigation"))
            api(project(":ui:core"))
            api(libs.navigation3.runtime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(project(":ui:resources"))
            api(project(":libs:reporting"))

            api(project(":services:flipcash"))
            api(project(":services:opencode"))
            implementation(project(":libs:encryption:keys"))
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.rinku.compose)
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":ui:components"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.bundles.haze)
            implementation(libs.zxing)
            implementation(libs.androidx.browser)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.localbroadcastmanager)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.compose.material3)
            api(libs.coil3)
            api(libs.coil3.network)
            api(project(":services:flipcash-compose"))
            api(project(":libs:reporting"))
            implementation(project(":libs:messaging"))
            api(project(":libs:permissions:public"))
            implementation(project(":libs:vibrator:public"))
            implementation(project(":apps:flipcash:shared:userflags"))
            api(project(":apps:flipcash:shared:theme"))
            api(libs.sodium.bindings)
            api(project(":vendor:kik:scanner"))
            api(project(":ui:core"))
            api(libs.navigation3.runtime)
            api(project(":vendor:tipkit:tipkit-m2"))
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
    packageOfResClass = "${Gradle.flipcashNamespace}.core.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = androidResAsComposeResources(),
    )
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
}
