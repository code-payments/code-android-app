import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.math.BigInteger

abstract class GenerateCurveTables : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val rustTableUrl: Property<String>

    @get:InputFile
    @get:Optional
    abstract val rustTableFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val forceRegenerate: Property<Boolean>

    init {
        // Default to false
        forceRegenerate.convention(false)

        outputs.upToDateWhen {
            // Check for -PforceCurveTables on command line OR task property
            val force = project.hasProperty("forceCurveTables") || forceRegenerate.get()

            if (force) {
                false
            } else if (rustTableUrl.isPresent && !rustTableFile.isPresent) {
                val outDir = outputDir.get().asFile
                val pricingExists = outDir.resolve("discrete_pricing_table.bin").exists()
                val cumulativeExists = outDir.resolve("discrete_cumulative_table.bin").exists()
                pricingExists && cumulativeExists
            } else {
                true
            }
        }
    }

    @TaskAction
    fun generate() {
        val content = when {
            rustTableUrl.isPresent -> {
                val url = rustTableUrl.get()
                logger.lifecycle("Downloading table from: $url")
                URI(url).toURL().readText()
            }
            rustTableFile.isPresent -> {
                logger.lifecycle("Reading table from: ${rustTableFile.get().asFile.path}")
                rustTableFile.get().asFile.readText()
            }
            else -> throw IllegalArgumentException("Must specify either rustTableUrl or rustTableFile")
        }

        val pricing = extractTable(content, "DISCRETE_PRICING_TABLE")
        val cumulative = extractTable(content, "DISCRETE_CUMULATIVE_VALUE_TABLE")

        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        writeBinaryTable(outDir.resolve("discrete_pricing_table.bin"), pricing)
        writeBinaryTable(outDir.resolve("discrete_cumulative_table.bin"), cumulative)

        logger.lifecycle("Generated curve tables:")
        logger.lifecycle("  Pricing: ${pricing.size} entries")
        logger.lifecycle("  Cumulative: ${cumulative.size} entries")
    }

    private fun extractTable(content: String, tableName: String): List<BigInteger> {
        val pattern = """pub static $tableName: &\[u128\] = &\[([\s\S]*?)\];""".toRegex()
        val match = pattern.find(content)
            ?: throw IllegalArgumentException("Could not find table: $tableName")

        val tableContent = match.groupValues[1]

        return tableContent.lines()
            .map { it.replace(Regex("//.*$"), "") }
            .flatMap { Regex("""\d+""").findAll(it).map { m -> m.value } }
            .map { BigInteger(it) }
    }

    private fun writeBinaryTable(file: java.io.File, values: List<BigInteger>) {
        val mask64 = BigInteger("FFFFFFFFFFFFFFFF", 16)

        file.outputStream().buffered().use { out ->
            val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)

            for (value in values) {
                val low = (value and mask64).toLong()
                val high = (value shr 64).toLong()

                buffer.clear()
                buffer.putLong(low)
                buffer.putLong(high)
                out.write(buffer.array())
            }
        }
    }
}