import kotlinx.serialization.Serializable
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(Libs.kotlinx_serialization_json)
    }
}

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.libs.emojis"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }
}

dependencies {
    implementation(Libs.inject)
    implementation(Libs.hilt)
    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_datetime)
    implementation(Libs.androidx_datastore)
}

private val emojiUrl = "https://unicode.org/Public/emoji/16.0/emoji-test.txt"
private val emojiKeywordsUrl =
    "https://raw.githubusercontent.com/unicode-org/cldr-json/refs/heads/main/cldr-json/cldr-annotations-full/annotations/en/annotations.json"
val emojiFile = File(projectDir, "emoji-test.txt") // Local cache
val keywordsFile = File(projectDir, "en-keywords.json") // Local cache
private val outputDir = File(projectDir, "src/main/kotlin/com/getcode/libs/emojis/generated")
private val outputFile = File(outputDir, "Emojis.kt")

// Define the task to fetch and generate emoji data
tasks.register("generateEmojiList") {
    description =
        "Fetches Unicode emoji list and generates categorized Kotlin source file if needed"
    group = "emoji"

    outputs.file(outputFile) // Task generates this file

    doLast {
        try {
            println("Executing generateEmojiList task")
            outputDir.mkdirs()
            if (!emojiFile.exists()) {
                println("Downloading emoji-test.txt from $emojiUrl")
                URL(emojiUrl).openStream().use { input ->
                    Files.copy(input, emojiFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                println("Using existing emoji-test.txt")
            }

            if (!keywordsFile.exists()) {
                println("Downloading CLDR annotations from $emojiKeywordsUrl")
                URL(emojiKeywordsUrl).openStream().use { input ->
                    Files.copy(input, keywordsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                println("Using existing CLDR annotations")
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
                        println("Group: $currentGroup")
                    }

                    line.startsWith("# subgroup:") -> {
                        currentSubgroup = line.removePrefix("# subgroup:").trim()
                        emojiCategories[currentGroup]?.getOrPut(currentSubgroup) { mutableListOf() }
                        emojiCategoriesNoSkinTones[currentGroup]?.getOrPut(currentSubgroup) { mutableListOf() }
                        println("Subgroup: $currentSubgroup")
                    }

                    line.isNotBlank() && !line.startsWith("#") -> {
                        val parts = line.split(";").map { it.trim() }
                        println("Line: $line")
                        println("Parts: $parts")
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

                            // Use CLDR data
                            val cldrAnnotation = cldrAnnotations?.get(unicode)?.jsonObject
                            val cldrKeywords = cldrAnnotation?.get("default")?.jsonArray?.mapNotNull { it.jsonPrimitive.toString().removeSurrounding("\"") }.orEmpty()
                            val allKeywords = (baseKeywords + cldrKeywords).distinct()

                            val emojiEntry = mutableMapOf(
                                "unicode" to unicode,
                                "name" to name,
                                "keywords" to allKeywords
                            )
                            emojiCategories[currentGroup]?.get(currentSubgroup)?.add(emojiEntry)
                            println("Added fully-qualified emoji: $unicode - $name")

                            val hasSkinTone = codePoints.any { it in 0x1F3FB..0x1F3FF }
                            if (!hasSkinTone) {
                                emojiCategoriesNoSkinTones[currentGroup]?.get(currentSubgroup)
                                    ?.add(emojiEntry)
                                println("Added to no-skin-tones: $unicode - $name")
                            }
                        } else {
                            println("Skipped non-fully-qualified: ${parts[0]} - Status: ${parts[1]}")
                        }
                    }
                }
            }

            // Filter out empty categories
            val nonEmptyCategories = emojiCategories.filter { (_, subgroups) ->
                subgroups.any { (_, emojis) -> emojis.isNotEmpty() }
            }
            val nonEmptyCategoriesNoSkinTones =
                emojiCategoriesNoSkinTones.filter { (_, subgroups) ->
                    subgroups.any { (_, emojis) -> emojis.isNotEmpty() }
                }

            // Generate a main Emojis.kt with category references and data class definition
            val mainFile = File(outputDir, "Emojis.kt")
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
            println("Generated Emojis.kt at ${mainFile.absolutePath}")

            // Generate separate files for each subgroup
            emojiCategories.forEach { (group, subgroups) ->
                subgroups.forEach { (subgroup, emojis) ->
                    val safeGroupName = group.replace("[^A-Za-z0-9]".toRegex(), "")
                    val safeSubgroupName = subgroup.replace("[^A-Za-z0-9]".toRegex(), "")
                    val subgroupFile =
                        File(outputDir, "${safeGroupName}${safeSubgroupName}Emojis.kt")
                    val subgroupCode = buildString {
                        appendLine("// Generated file - Do not edit manually")
                        appendLine("package com.getcode.libs.emojis.generated")
                        appendLine()
                        appendLine("object ${safeGroupName}${safeSubgroupName}Emojis {")
                        appendLine("    val categorized = ${if (emojis.isEmpty()) "emptyList<Emoji>()" else "listOf("}")
                        if (emojis.isNotEmpty()) {
                            appendLine("        ${emojis.joinToString(",\n        ") { "Emoji(\"${it["unicode"]}\", \"${it["name"]}\", listOf(${it["keywords"]?.let { k -> (k as List<String>).joinToString { "\"$it\"" } }}))" }}")
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
                            appendLine("        ${noSkinTones.joinToString(",\n        ") { "Emoji(\"${it["unicode"]}\", \"${it["name"]}\", listOf(${it["keywords"]?.let { k -> (k as List<String>).joinToString { "\"$it\"" } }}))" }}")
                            appendLine("    )")
                        }
                        appendLine("}")
                    }.trimIndent()
                    subgroupFile.writeText(subgroupCode)
                    println("Generated ${safeGroupName}${safeSubgroupName}Emojis.kt at ${subgroupFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            println("Error in generateEmojiList: ${e.message}")
            throw e
        }
    }
}