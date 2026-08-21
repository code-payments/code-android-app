package com.getcode.buildlogic.testfixtures

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Compiles the cross-platform fixtures into `commonTest` as Kotlin constants.
 *
 * The parity gate is only worth something if it runs on *both* platforms, and Kotlin/Native test
 * binaries ship no resource bundle -- `NSBundle.pathForResource` finds nothing there, so a
 * resource-based loader quietly only ever runs on the JVM. Generating a source file instead makes
 * the same fixtures readable from every target with no platform code at all.
 */
abstract class GenerateTestFixtures : DefaultTask() {

    /** Package the generated `TestFixtures.kt` is declared in. */
    @get:Input
    abstract val packageName: Property<String>

    @get:InputDirectory
    abstract val fixtures: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = packageName.get()
        val files = fixtures.get().asFile.listFiles().orEmpty().sortedBy { it.name }
        val destination = outputDirectory.get().asFile
            .resolve(target.replace('.', '/'))
            .resolve("TestFixtures.kt")
        destination.parentFile.mkdirs()

        destination.writeText(
            buildString {
                appendLine("package $target")
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
