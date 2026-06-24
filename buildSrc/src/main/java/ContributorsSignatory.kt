import java.io.File
import java.util.Properties

/**
 * Reads the contributor signing config from `contributors.properties` at the
 * given [rootDir].
 *
 * Takes the root directory as a [File] rather than a [org.gradle.api.Project] so
 * callers don't reach into another project (`rootProject.file(...)`), which
 * Isolated Projects forbids. Paths resolve as `Project.file` did: relative
 * entries against [rootDir], absolute entries as-is.
 */
class ContributorsSignatory(rootDir: File) {
    private val signingProperties: Properties = Properties().apply {
        rootDir.resolve("contributors.properties").inputStream().use { load(it) }
    }

    val keystore: File = rootDir.resolve(signingProperties.getProperty("KEYSTORE_FILE"))
    val keystorePassword: String = signingProperties.getProperty("KEYSTORE_PASS")
    val keyAlias: String = signingProperties.getProperty("KEY_ALIAS")
    val keyPassword: String = signingProperties.getProperty("KEY_PASS")
}
