import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

/**
 * Retrieve the project local properties if they are available.
 * If there is no local properties file then an empty set of properties is returned.
 */
fun gradleLocalProperties(projectRootDir: File): Properties {
    val properties = Properties()
    val localProperties = File(projectRootDir, "local.properties")
    if (localProperties.isFile) {
        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    } else {
        println("Gradle local properties file not found at $localProperties")
    }
    return properties
}

fun tryReadProperty(projectRootDir: File, property: String, fallback: String  = ""): String {
    return gradleLocalProperties(projectRootDir).getProperty(property)
        ?: (System.getenv(property)) ?: fallback
}
