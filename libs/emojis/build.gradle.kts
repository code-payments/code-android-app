import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    kotlinOptions {
        jvmTarget = Versions.java
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
}

private val emojiUrl = "https://unicode.org/Public/emoji/16.0/emoji-test.txt"
val emojiFile = File(projectDir, "emoji-test.txt") // Local cache
private val outputDir = File(projectDir, "src/main/kotlin/com/getcode/libs/emojis/generated")
private val outputFile = File(outputDir, "Emojis.kt")

// Define the task to fetch and generate emoji data
tasks.register("generateEmojiList") {
    description = "Fetches Unicode emoji list and generates categorized Kotlin source file if needed"
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

            val emojiText = emojiFile.readText()
            val emojiCategories = mutableMapOf<String, MutableMap<String, MutableList<Pair<String, String>>>>()
            val emojiCategoriesNoSkinTones = mutableMapOf<String, MutableMap<String, MutableList<Pair<String, String>>>>()
            var currentGroup = "Uncategorized"
            var currentSubgroup = "Uncategorized"

            emojiText.lines().forEach { line ->
                when {
                    line.startsWith("# group:") -> {
                        currentGroup = line.removePrefix("# group:").trim()
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
                        if (parts.size > 1) {
                            val codePoints = parts[0].split(" ").map { it.toInt(16) }
                            val emoji = codePoints.map { codePoint ->
                                if (codePoint <= 0xFFFF) {
                                    codePoint.toChar().toString()
                                } else {
                                    String(Character.toChars(codePoint))
                                }
                            }.joinToString("")
                            val name = line.split("#")[1].trim().split(" ").drop(2).joinToString(" ")

                            val emojiWithName = Pair(emoji, name)
                            emojiCategories[currentGroup]?.get(currentSubgroup)?.add(emojiWithName)

                            val hasSkinTone = codePoints.any { it in 0x1F3FB..0x1F3FF }
                            if (!hasSkinTone) {
                                emojiCategoriesNoSkinTones[currentGroup]?.get(currentSubgroup)?.add(emojiWithName)
                            }
                        }
                    }
                }
            }

            // Generate a main EmojiData.kt with category references
            val mainFile = File(outputDir, "Emojis.kt")
            val mainCode = buildString {
                appendLine("// Generated file - Do not edit manually")
                appendLine("package com.getcode.libs.emojis.generated")
                appendLine()
                appendLine("object Emojis {")
                appendLine("    val categorized = mapOf(")
                emojiCategories.forEach { (group, subgroups) ->
                    appendLine("        \"$group\" to mapOf(")
                    subgroups.forEach { (subgroup, emojis) ->
                        val safeGroupName = group.replace("[^A-Za-z0-9]".toRegex(), "")
                        val safeSubgroupName = subgroup.replace("[^A-Za-z0-9]".toRegex(), "")
                        appendLine("            \"$subgroup\" to ${safeGroupName}${safeSubgroupName}Emojis.categorized,")
                    }
                    appendLine("        ),")
                }
                appendLine("    )")
                appendLine()
                appendLine("    val categorizedNoSkinTones = mapOf(")
                emojiCategoriesNoSkinTones.forEach { (group, subgroups) ->
                    appendLine("        \"$group\" to mapOf(")
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
                    val subgroupFile = File(outputDir, "${safeGroupName}${safeSubgroupName}Emojis.kt")
                    val subgroupCode = buildString {
                        appendLine("// Generated file - Do not edit manually")
                        appendLine("package com.getcode.libs.emojis.generated")
                        appendLine()
                        appendLine("object ${safeGroupName}${safeSubgroupName}Emojis {")
                        appendLine("    val categorized = ${if (emojis.isEmpty()) "emptyList<Pair<String, String>>()" else "listOf("}")
                        if (emojis.isNotEmpty()) {
                            appendLine("        ${emojis.joinToString(",\n        ") { "Pair(\"${it.first}\", \"${it.second}\")" }}")
                            appendLine("    )")
                        }
                        appendLine()
                        appendLine("    val categorizedNoSkinTones = ${if (emojiCategoriesNoSkinTones[group]?.get(subgroup)?.isEmpty() != false) "emptyList<Pair<String, String>>()" else "listOf("}")
                        val noSkinTones = emojiCategoriesNoSkinTones[group]?.get(subgroup) ?: emptyList()
                        if (noSkinTones.isNotEmpty()) {
                            appendLine("        ${noSkinTones.joinToString(",\n        ") { "Pair(\"${it.first}\", \"${it.second}\")" }}")
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