plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.persistence.sources"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.paging.common)
            implementation(libs.javax.inject)

            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:shared:persistence:db"))
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":libs:encryption:base58"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":libs:encryption:utils"))
            implementation(project(":services:flipcash"))
        }
        androidMain.dependencies {
            implementation(libs.bundles.hilt)
            implementation(libs.androidx.room.ktx)
            implementation(libs.androidx.paging.runtime)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(project(":services:flipcash:test-fixtures"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
}
