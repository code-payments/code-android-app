package com.getcode.buildlogic.analytics

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private fun page(catalogue: RegularFileProperty): String = try {
    AnalyticsCatalogue.document(AnalyticsCatalogue.parse(catalogue.get().asFile.readText()))
} catch (malformed: CatalogueException) {
    throw GradleException(malformed.message.orEmpty(), malformed)
}

/** Writes `EVENTS.md` from `events.toml`. The page is checked in, so this writes into the module. */
abstract class WriteAnalyticsEventsPage : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalogue: RegularFileProperty

    @get:OutputFile
    abstract val page: RegularFileProperty

    @TaskAction
    fun write() = page.get().asFile.writeText(page(catalogue))
}

/** Fails when the checked-in `EVENTS.md` is not what `events.toml` generates. */
abstract class CheckAnalyticsEventsPage : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalogue: RegularFileProperty

    /** Read only if it exists: a missing page is reported, not a missing-input failure. */
    @get:Internal
    abstract val page: RegularFileProperty

    /** The command the failure tells the reader to run. */
    @get:Input
    abstract val fixCommand: Property<String>

    @TaskAction
    fun check() {
        val file = page.get().asFile
        val expected = page(catalogue)
        if (!file.exists() || file.readText() != expected) {
            throw GradleException(
                "${file.name} is out of date with events.toml. Run `${fixCommand.get()}` and commit the result.",
            )
        }
    }
}
