import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class GenerateEmojiList : DefaultTask() {

    @get:Input
    abstract val emojiUrl: Property<String>

    @get:Input
    abstract val emojiKeywordsUrl: Property<String>

    @get:Internal
    abstract val emojiCacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        description = "Fetches Unicode emoji list and generates categorized Kotlin source file if needed"
        group = "emoji"

        onlyIf {
            !outputDir.get().asFile.resolve("Emojis.kt").exists()
        }
    }

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        val cacheDir = emojiCacheDir.get().asFile
        val emojiFile = File(cacheDir, "emoji-test.txt")
        val keywordsFile = File(cacheDir, "en-keywords.json")

        outDir.mkdirs()
        if (!emojiFile.exists()) {
            logger.lifecycle("Downloading emoji-test.txt")
            URI(emojiUrl.get()).toURL().openStream().use { input ->
                Files.copy(input, emojiFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        if (!keywordsFile.exists()) {
            logger.lifecycle("Downloading CLDR annotations")
            URI(emojiKeywordsUrl.get()).toURL().openStream().use { input ->
                Files.copy(input, keywordsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        val cldrData = json.parseToJsonElement(keywordsFile.readText()).jsonObject
        val cldrAnnotations = cldrData["annotations"]?.jsonObject?.get("annotations")?.jsonObject

        val emojiText = emojiFile.readText()
        val emojiCategories =
            mutableMapOf<String, MutableMap<String, MutableList<MutableMap<String, Any>>>>()
        val emojiCategoriesNoSkinTones =
            mutableMapOf<String, MutableMap<String, MutableList<MutableMap<String, Any>>>>()
        var currentGroup = "Uncategorized"
        var currentSubgroup = "Uncategorized"

        emojiText.lines().forEach { line ->
            when {
                line.startsWith("# group:") -> {
                    val groupName = line.removePrefix("# group:").trim()
                    currentGroup = when (groupName) {
                        "Smileys & Emotion", "People & Body" -> "Smileys & People"
                        else -> groupName
                    }
                    emojiCategories.getOrPut(currentGroup) { mutableMapOf() }
                    emojiCategoriesNoSkinTones.getOrPut(currentGroup) { mutableMapOf() }
                }

                line.startsWith("# subgroup:") -> {
                    currentSubgroup = line.removePrefix("# subgroup:").trim()
                    emojiCategories[currentGroup]?.getOrPut(currentSubgroup) { mutableListOf() }
                    emojiCategoriesNoSkinTones[currentGroup]?.getOrPut(currentSubgroup) { mutableListOf() }
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val parts = line.split(";").map { it.trim() }
                    if (parts.size > 1 && parts[1].contains("fully-qualified")) {
                        val codePoints = parts[0].split(" ").map { it.toInt(16) }
                        val unicode = codePoints.map { codePoint ->
                            if (codePoint <= 0xFFFF) {
                                codePoint.toChar().toString()
                            } else {
                                String(Character.toChars(codePoint))
                            }
                        }.joinToString("")
                        val nameParts = line.split("#")[1].trim().split(" ")
                        val name = nameParts.drop(2).joinToString(" ")
                        val baseKeywords = name.split(" ").filter { it.isNotBlank() }

                        val cldrAnnotation = cldrAnnotations?.get(unicode)?.jsonObject
                        val cldrKeywords = cldrAnnotation?.get("default")?.jsonArray
                            ?.mapNotNull { it.jsonPrimitive.toString().removeSurrounding("\"") }
                            .orEmpty()
                        val allKeywords = (baseKeywords + cldrKeywords).distinct()

                        val emojiEntry = mutableMapOf(
                            "unicode" to unicode,
                            "name" to name,
                            "keywords" to allKeywords
                        )
                        emojiCategories[currentGroup]?.get(currentSubgroup)?.add(emojiEntry)

                        val hasSkinTone = codePoints.any { it in 0x1F3FB..0x1F3FF }
                        if (!hasSkinTone) {
                            emojiCategoriesNoSkinTones[currentGroup]?.get(currentSubgroup)
                                ?.add(emojiEntry)
                        }
                    }
                }
            }
        }

        val nonEmptyCategories = emojiCategories.filter { (_, subgroups) ->
            subgroups.any { (_, emojis) -> emojis.isNotEmpty() }
        }
        val nonEmptyCategoriesNoSkinTones =
            emojiCategoriesNoSkinTones.filter { (_, subgroups) ->
                subgroups.any { (_, emojis) -> emojis.isNotEmpty() }
            }

        val mainFile = File(outDir, "Emojis.kt")
        val mainCode = buildString {
            appendLine("// Generated file - Do not edit manually")
            appendLine("package com.getcode.libs.emojis.generated")
            appendLine()
            appendLine("data class Emoji(")
            appendLine("    val unicode: String,")
            appendLine("    val name: String,")
            appendLine("    val keywords: List<String>")
            appendLine(")")
            appendLine()
            appendLine("enum class Category(val displayName: String) {")
            nonEmptyCategories.keys.forEach { group ->
                val enumName = group.replace("[^A-Za-z0-9]".toRegex(), "").uppercase()
                appendLine("    $enumName(\"$group\"),")
            }
            appendLine("    FREQUENT(\"Frequently Used\"),")
            appendLine("}")
            appendLine()
            appendLine("object Emojis {")
            appendLine("    val categorized = mapOf(")
            nonEmptyCategories.forEach { (group, subgroups) ->
                val enumName = group.replace("[^A-Za-z0-9]".toRegex(), "").uppercase()
                appendLine("        Category.$enumName to mapOf(")
                subgroups.forEach { (subgroup, _) ->
                    val safeGroupName = group.replace("[^A-Za-z0-9]".toRegex(), "")
                    val safeSubgroupName = subgroup.replace("[^A-Za-z0-9]".toRegex(), "")
                    appendLine("            \"$subgroup\" to ${safeGroupName}${safeSubgroupName}Emojis.categorized,")
                }
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine()
            appendLine("    val categorizedNoSkinTones = mapOf(")
            nonEmptyCategoriesNoSkinTones.forEach { (group, subgroups) ->
                val enumName = group.replace("[^A-Za-z0-9]".toRegex(), "").uppercase()
                appendLine("        Category.$enumName to mapOf(")
                subgroups.forEach { (subgroup, _) ->
                    val safeGroupName = group.replace("[^A-Za-z0-9]".toRegex(), "")
                    val safeSubgroupName = subgroup.replace("[^A-Za-z0-9]".toRegex(), "")
                    appendLine("            \"$subgroup\" to ${safeGroupName}${safeSubgroupName}Emojis.categorizedNoSkinTones,")
                }
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("}")
        }.trimIndent()
        mainFile.writeText(mainCode)

        emojiCategories.forEach { (group, subgroups) ->
            subgroups.forEach { (subgroup, emojis) ->
                val safeGroupName = group.replace("[^A-Za-z0-9]".toRegex(), "")
                val safeSubgroupName = subgroup.replace("[^A-Za-z0-9]".toRegex(), "")
                val subgroupFile =
                    File(outDir, "${safeGroupName}${safeSubgroupName}Emojis.kt")
                @Suppress("UNCHECKED_CAST")
                val subgroupCode = buildString {
                    appendLine("// Generated file - Do not edit manually")
                    appendLine("package com.getcode.libs.emojis.generated")
                    appendLine()
                    appendLine("object ${safeGroupName}${safeSubgroupName}Emojis {")
                    appendLine("    val categorized = ${if (emojis.isEmpty()) "emptyList<Emoji>()" else "listOf("}")
                    if (emojis.isNotEmpty()) {
                        appendLine("        ${emojis.joinToString(",\n        ") { "Emoji(\"${it["unicode"]}\", \"${it["name"]}\", listOf(${(it["keywords"] as List<String>).joinToString { "\"$it\"" }}))" }}")
                        appendLine("    )")
                    }
                    appendLine()
                    appendLine(
                        "    val categorizedNoSkinTones = ${
                            if (emojiCategoriesNoSkinTones[group]?.get(
                                    subgroup
                                )?.isEmpty() != false
                            ) "emptyList<Emoji>()" else "listOf("
                        }"
                    )
                    val noSkinTones =
                        emojiCategoriesNoSkinTones[group]?.get(subgroup) ?: emptyList()
                    if (noSkinTones.isNotEmpty()) {
                        appendLine("        ${noSkinTones.joinToString(",\n        ") { "Emoji(\"${it["unicode"]}\", \"${it["name"]}\", listOf(${(it["keywords"] as List<String>).joinToString { "\"$it\"" }}))" }}")
                        appendLine("    )")
                    }
                    appendLine("}")
                }.trimIndent()
                subgroupFile.writeText(subgroupCode)
            }
        }
        val totalEmojis = emojiCategories.values.sumOf { it.values.sumOf { e -> e.size } }
        logger.lifecycle("Generated $totalEmojis emojis across ${emojiCategories.size} categories")
    }
}
