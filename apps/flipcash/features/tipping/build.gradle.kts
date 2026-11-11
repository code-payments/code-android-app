plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

// New Chat's view model is in commonMain so the Puppet host can run it on the JVM. The rest of the
// module (find by username, the group forms, requests) is still Android-only, in androidMain.
kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.features.tipping"
        // No resources of its own; the screens read dependency strings through R.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:shared:tokens"))
            implementation(project(":services:opencode"))
            implementation(project(":apps:flipcash:core"))
            implementation(project(":libs:coroutines"))
            implementation(project(":ui:core"))
            implementation(libs.javax.inject)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.rinku.compose)
            implementation(project(":libs:logging"))
            implementation(project(":ui:components"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))
            implementation(project(":apps:flipcash:core-ui"))

            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":services:flipcash"))
            implementation(project(":libs:messaging"))
            implementation(project(":apps:flipcash:shared:amount-entry"))
            implementation(project(":apps:flipcash:shared:bills"))
            implementation(project(":apps:flipcash:shared:blob"))
            implementation(project(":apps:flipcash:shared:featureflags"))
            implementation(project(":apps:flipcash:shared:chat"))
            implementation(project(":apps:flipcash:shared:chat-ui"))
            implementation(project(":apps:flipcash:shared:common-ui"))
            implementation(project(":apps:flipcash:shared:shareable"))
            implementation(project(":apps:flipcash:shared:tipping"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(project(":ui:resources:test-fixtures"))
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
