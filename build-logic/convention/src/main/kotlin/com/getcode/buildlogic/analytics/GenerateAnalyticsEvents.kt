package com.getcode.buildlogic.analytics

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates the analytics builders and value enums from `events.toml`.
 *
 * The output directory is cleared first, so an entry removed from the catalogue removes its
 * generated file rather than leaving a stale builder behind.
 */
@CacheableTask
abstract class GenerateAnalyticsEvents : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalogue: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sources = try {
            AnalyticsCatalogue.emit(AnalyticsCatalogue.parse(catalogue.get().asFile.readText()))
        } catch (malformed: CatalogueException) {
            throw GradleException(malformed.message.orEmpty(), malformed)
        }

        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        sources.forEach { (path, source) ->
            root.resolve(path).apply { parentFile.mkdirs() }.writeText(source)
        }
    }
}
