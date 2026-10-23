plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.persistence.db"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation(libs.androidx.paging.common)

            implementation(project(":libs:models"))
            implementation(project(":libs:encryption:base58"))
            implementation(project(":libs:encryption:utils"))
            implementation(project(":libs:logging"))

            implementation(project(":services:flipcash"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.room.ktx)
            implementation(libs.androidx.paging.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(libs.androidx.room.testing)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}
