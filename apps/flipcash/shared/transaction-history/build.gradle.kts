import com.getcode.buildlogic.resources.androidResAsComposeResources

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.ksp)
}

// Home of the transaction-history data engine (ActivityFeedCoordinator / ActivityFeedUpdater),
// consolidated out of the deprecated :shared:activityfeed. Mirrors that module's dependencies.
// See docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md.
kotlin {
    android {
        namespace = "com.flipcash.shared.transactionhistory"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:shared:persistence:sources"))
            implementation(project(":apps:flipcash:shared:common-ui"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:datetime"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":libs:encryption:utils"))
            implementation(project(":libs:logging"))
            implementation(project(":ui:core"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.androidx.paging.common)
            implementation(libs.javax.inject)
            compileOnly(project(":apps:flipcash:shared:persistence:db"))
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
            implementation(libs.bundles.haze)
            implementation(project(":libs:coroutines"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:components"))

            implementation(libs.bundles.room)
            implementation(libs.androidx.paging.runtime)
            implementation(libs.compose.paging)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(project(":ui:resources:test-fixtures"))
            // Screenshot renders only: FlipcashPreview for the theme, ExchangeStub for the currency flag
            // LocalExchange would otherwise resolve to ExchangeNull.
            implementation(project(":apps:flipcash:shared:theme"))
            implementation(project(":services:opencode-compose"))
        }
    }
}

compose.resources {
    packageOfResClass = "com.flipcash.shared.transactionhistory.generated.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = androidResAsComposeResources(),
    )
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
