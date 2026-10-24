plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.userflags"
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.javax.inject)
            implementation(libs.kotlinx.serialization.json)

            implementation(project(":libs:coroutines"))
            implementation(project(":libs:datetime"))
            implementation(project(":libs:messaging"))
            implementation(project(":libs:currency-math"))

            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
        }
        androidMain.dependencies {
            implementation(libs.bundles.hilt)
            implementation(libs.androidx.datastore)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.mockito.kotlin)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
