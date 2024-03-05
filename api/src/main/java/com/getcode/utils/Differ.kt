package com.getcode.utils

import timber.log.Timber
import kotlin.math.max

fun printDiff(title: String, one: String, two: String) {
    printDiff(title, listOf(one), listOf(two))
}

fun printDiff(title: String, one: List<String>, two: List<String>) {
    val lineSeparator = "\n -\n"

    Timber.d("✗ $title")
    Timber.d("|----------------------------------------------------------------------")

    val lines = (0 until max(one.count(), two.count())).joinToString(lineSeparator) { i ->
        var oneValue = "-"
        if (i < one.count()) {
            oneValue = one[i]
        }

        var twoValue = "-"
        if (i < two.count()) {
            twoValue = two[i]
        }

        val content = """
            | ${if (oneValue != twoValue) "✗" else "✓"} 1: $oneValue
            | ${if (oneValue != twoValue) "✗" else "✓"} 2: $twoValue
        """.trimIndent()

        content
    }

    Timber.d(lines)
    Timber.d("|----------------------------------------------------------------------")
}

fun printMatch(title: String) {
    Timber.d("✓ $title")
}