plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.tokens"
        // No resources of its own; SwapViewModel and TokenInfoViewModel read dependency strings through R.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":apps:flipcash:shared:tokens:core"))

            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:shared:persistence:sources"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":libs:logging"))
            implementation(project(":ui:navigation"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.javax.inject)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(project(":ui:core"))
            implementation(project(":ui:components"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":services:flipcash"))

            implementation(libs.androidx.datastore)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.bundles.haze)

            implementation(project(":apps:flipcash:shared:amount-entry"))
            implementation(project(":apps:flipcash:shared:funding"))
            implementation(project(":apps:flipcash:shared:transaction-history"))
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":apps:flipcash:shared:featureflags"))
            implementation(project(":apps:flipcash:shared:onramp:coinbase"))
            implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
            implementation(project(":apps:flipcash:shared:shareable"))
            implementation(project(":apps:flipcash:shared:userflags"))
            implementation(project(":libs:datetime"))
            implementation(project(":libs:messaging"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(project(":ui:resources:test-fixtures"))
            implementation(project(":libs:currency-math:test-fixtures"))
            implementation(libs.mockito.kotlin)
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
