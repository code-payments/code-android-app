package com.getcode.buildlogic.testfixtures

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configures [GenerateTestFixtures] for the module.
 *
 * ```
 * testFixtures {
 *     packageName = "com.getcode.vendor"
 * }
 * ```
 */
abstract class TestFixturesExtension {

    /** Package the generated `TestFixtures.kt` is declared in. Required. */
    abstract val packageName: Property<String>

    /** Directory of fixture files to compile in. Defaults to `src/commonTest/resources`. */
    abstract val fixtures: DirectoryProperty
}
