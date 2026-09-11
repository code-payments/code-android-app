plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest` --
// see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.flipcash.libs.currency.math.curve"
}

kotlin {
    android {
        namespace = "com.flipcash.libs.currency.math.curve"
        compileSdk = 37
        minSdk = 29
        withHostTest {}

        // `flipcash.kmp.test.fixtures` compiles this module's fixtures --
        // discrete_pricing_table.bin.b64 / discrete_cumulative_table.bin.b64, ~4.5MB each -- into
        // a single ~8.7MB generated TestFixtures.kt on commonTest (see GenerateTestFixtures.kt).
        // AGP's `apps:flipcash:app` lint run aggregates dependency modules' test sources when it
        // reads source directories straight off disk, and lint's PSI/UAST parser hard-caps source
        // files at 20MB pre-escaping headroom -- so a whole-project run tries to parse this
        // generated fixture file and fails with LintError. Excluding this module's test sources
        // from lint keeps that generated file out of the aggregated scan without touching the
        // hand-written commonMain/androidMain sources lint still checks.
        lint {
            ignoreTestSources = true
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ionspin.bignum)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
