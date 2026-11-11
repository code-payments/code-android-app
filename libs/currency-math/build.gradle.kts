import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.apple)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// The gate vectors live once, in the device-test assets; compile those into commonTest so the same
// files gate every target.
testFixtures {
    packageName = "com.flipcash.libs.currency.math"
    fixtures = layout.projectDirectory.dir("src/androidDeviceTest/assets")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: both JVM targets (Android and desktop), where the decimal type is java.math.BigDecimal
    // and the tables are read with java.io. On JS the decimal type is CommonBigDecimal.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
            // nonJvm: Node and Apple, where the decimal type is CommonBigDecimal.
            group("nonJvm") {
                withJs()
                withApple()
            }
        }
    }

    android {
        namespace = "${Gradle.codeNamespace}.libs.currency.math"
        // The KMP android target skips res and assets unless this is on, and the curve tables
        // ship as assets. Without it the app has no tables and crashes on the first token balance.
        androidResources { enable = true }
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:currency-math:discrete-curve"))
        }
        // The desktop build ships the same table files Android packs as assets.
        jvmMain {
            resources.srcDir("src/androidMain/assets")
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(project(":libs:currency-math:test-fixtures"))
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.junit)
            implementation(libs.androidx.test.runner)
        }
        appleMain.dependencies {
            implementation(libs.ionspin.bignum)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

val generateCurveTables by tasks.registering(GenerateCurveTables::class) {
    rustTableUrl.set("https://raw.githubusercontent.com/code-payments/flipcash-program/refs/heads/main/api/src/table.rs")
    outputDir.set(layout.projectDirectory.dir("src/androidMain/assets"))
    forceViaGradleProperty.set(providers.gradleProperty("forceCurveTables").map { true }.orElse(false))

    // Always regenerate (useful for CI or development)
    // TODO: enable for CI when repo is public
    // forceRegenerate.set(true)
}

// Make sure tables are generated before either platform packs them. The KMP android target has no
// preBuild task, so hook the compilations instead.
tasks.matching { it.name == "compileAndroidMain" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(generateCurveTables)
}

// The iOS simulator runs the test binary from an unrelated working directory, so tell the Native
// table loader where the tables are. Simulator tests only see variables prefixed SIMCTL_CHILD_.
tasks.withType<KotlinNativeTest>().configureEach {
    val assets = layout.projectDirectory.dir("src/androidMain/assets").asFile.absolutePath
    environment("FLIPCASH_CURVE_ASSETS", assets)
    if (this is KotlinNativeSimulatorTest) environment("SIMCTL_CHILD_FLIPCASH_CURVE_ASSETS", assets)
}
