plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.currency.math"
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.javax.inject)
            implementation(project(":libs:currency-math:discrete-curve"))
        }
        androidMain.dependencies {
            implementation(libs.bundles.hilt)
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
        jvmTest.dependencies {
            implementation(kotlin("test"))
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
