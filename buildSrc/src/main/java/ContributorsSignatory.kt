import org.gradle.api.Project
import java.util.Properties


class ContributorsSignatory(project: Project) {
    private val signingProperties: Properties

    init {
        val propertiesFile = project.file("contributors.properties")
        signingProperties = Properties().apply {
            load(propertiesFile.inputStream())
        }
    }

    val keystore = project.file(signingProperties.getProperty("KEYSTORE_FILE"))
    val keystorePassword: String = signingProperties.getProperty("KEYSTORE_PASS")
    val keyAlias: String = signingProperties.getProperty("KEY_ALIAS")
    val keyPassword: String = signingProperties.getProperty("KEY_PASS")
}