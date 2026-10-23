plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.funding"
        withHostTest {}
        androidResources { enable = true }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.bundles.hilt)
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:logging"))
            implementation(project(":ui:core"))
            implementation(project(":ui:components"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":apps:flipcash:shared:featureflags"))
            implementation(project(":apps:flipcash:shared:tokens:core"))
            implementation(project(":apps:flipcash:shared:userflags"))
            implementation(project(":libs:messaging"))
            implementation(project(":services:flipcash"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
}
