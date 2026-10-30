plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.features.balance"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:card-expand"))
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":apps:flipcash:shared:funding"))
            implementation(project(":apps:flipcash:shared:tokens"))
            implementation(project(":apps:flipcash:shared:transaction-history"))
            implementation(project(":apps:flipcash:shared:userflags"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":services:opencode-compose"))
            implementation(project(":libs:analytics-events"))
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":ui:components"))
            implementation(project(":ui:core"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.androidx.paging.common)
            implementation(libs.javax.inject)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.compose.paging)
            implementation(project(":apps:flipcash:shared:featureflags"))
            implementation(project(":libs:datetime"))
            implementation(project(":libs:messaging"))
            implementation(project(":libs:permissions:bindings"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
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
