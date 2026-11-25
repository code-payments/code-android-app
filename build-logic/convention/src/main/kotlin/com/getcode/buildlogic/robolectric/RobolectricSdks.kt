package com.getcode.buildlogic.robolectric

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.util.Properties

private val SDK_ALIASES = listOf("robolectric-sdk24", "robolectric-sdk32", "robolectric-sdk34", "robolectric-sdk35", "robolectric-sdk36")

/**
 * Feeds Robolectric its android-all jars from Gradle instead of letting each test JVM fetch them.
 *
 * Left alone, Robolectric's MavenDependencyResolver downloads a 150-220 MB jar per SDK level into
 * `~/.m2` from inside the test JVM. Neither CI workflow caches `~/.m2`, so every run downloads
 * again, and a failed fetch fails the whole test class in `classMethod` with
 * `AssertionError at MavenArtifactFetcher.java:129`. Resolving the jars as Gradle dependencies puts
 * them in the Gradle dependency cache, which CI already restores, and moves the download out of the
 * tests.
 *
 * Robolectric reads the jars' locations from the file named by the `robolectric-deps.properties`
 * system property. An SDK missing from that file fails with "no artifacts found for
 * DependencyJar{...}" rather than falling back to a download, so a Robolectric bump that renames
 * its jars fails loudly.
 */
fun Project.useGradleResolvedRobolectricSdks(testTaskFilter: (Test) -> Boolean = { true }) {
    // A module can apply more than one convention that calls this.
    if (configurations.findByName("robolectricSdk36") != null) return
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    // One configuration per SDK: the jars are versions of one module, so a shared configuration
    // would resolve the conflict to the newest and drop the rest.
    val sdks = files(
        SDK_ALIASES.map { alias ->
            configurations.create(alias.replace("-s", "S")) {
                isCanBeConsumed = false
                isCanBeResolved = true
                isTransitive = false
            }.also { dependencies.add(it.name, libs.findLibrary(alias).get()) }
        },
    )

    tasks.withType<Test>().configureEach {
        if (!testTaskFilter(this)) return@configureEach
        val depsFile = File(temporaryDir, "robolectric-deps.properties")
        val args = RobolectricSdkArgs(sdks, depsFile)
        jvmArgumentProviders.add(args)
        doFirst { args.writeDepsFile() }
    }
}

internal class RobolectricSdkArgs(
    // The jar names carry the version and the content is fixed per version, so where the Gradle
    // cache lives on a machine must not affect the test task's cache key.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val jars: FileCollection,
    @get:Internal
    val depsFile: File,
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric-deps.properties=${depsFile.absolutePath}")

    fun writeDepsFile() {
        val props = Properties()
        jars.files.forEach { jar ->
            val version = jar.name.removePrefix("$ARTIFACT-").removeSuffix(".jar")
            props.setProperty("org.robolectric:$ARTIFACT:$version", jar.absolutePath)
        }
        depsFile.parentFile.mkdirs()
        // Properties.store escapes the ':' in the coordinates, which load() would otherwise read as
        // the key/value separator.
        depsFile.writer().use { props.store(it, null) }
    }

    private companion object {
        const val ARTIFACT = "android-all-instrumented"
    }
}
