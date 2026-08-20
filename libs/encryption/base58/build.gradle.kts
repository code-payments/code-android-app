plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

/**
 * Compiles the cross-platform fixtures into `commonTest` as Kotlin constants.
 *
 * The parity gate is only worth something if it runs on *both* platforms, and Kotlin/Native test
 * binaries ship no resource bundle -- `NSBundle.pathForResource` finds nothing there, so a
 * resource-based loader quietly only ever runs on the JVM. Generating a source file instead makes
 * the same fixtures readable from every target with no platform code at all.
 */
abstract class GenerateTestFixtures : DefaultTask() {

    @get:InputDirectory
    abstract val fixtures: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val files = fixtures.get().asFile.listFiles().orEmpty().sortedBy { it.name }
        val destination = outputDirectory.get().asFile
            .resolve("com/getcode/vendor/TestFixtures.kt")
        destination.parentFile.mkdirs()

        destination.writeText(
            buildString {
                appendLine("package com.getcode.vendor")
                appendLine()
                appendLine("// Generated from src/commonTest/resources -- do not edit.")
                appendLine()
                appendLine("private val FIXTURES: Map<String, String> = mapOf(")
                files.forEach { file ->
                    append("    \"").append(file.name).append("\" to \"")
                    append(file.readText().escapeForKotlin())
                    appendLine("\",")
                }
                appendLine(")")
                appendLine()
                appendLine("/** Reads a fixture compiled in from `src/commonTest/resources/`. */")
                appendLine("fun readTestResource(name: String): String =")
                append("    requireNotNull(FIXTURES[name]) { \"unknown fixture '")
                appendLine("\$name'\" }")
            }
        )
    }

    private fun String.escapeForKotlin(): String = buildString(length) {
        this@escapeForKotlin.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}

val generateTestFixtures = tasks.register<GenerateTestFixtures>("generateTestFixtures") {
    fixtures.set(layout.projectDirectory.dir("src/commonTest/resources"))
    outputDirectory.set(layout.buildDirectory.dir("generated/testFixtures"))
}

kotlin {
    android {
        namespace = "com.getcode.encryption.base58"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain {
            // Pure Kotlin -- no external dependencies needed.
        }
        androidMain {
            // MessageDigest + BigInteger -- JDK only; no extra Gradle deps.
        }
        commonTest {
            kotlin.srcDir(generateTestFixtures)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
