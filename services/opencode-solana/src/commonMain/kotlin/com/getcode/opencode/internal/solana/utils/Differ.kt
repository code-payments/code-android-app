package com.getcode.opencode.internal.solana.utils

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.utils.Log
import kotlin.math.max

@InternalOpenCodeApi public fun printDiff(title: String, one: String, two: String) {
    printDiff(title, listOf(one), listOf(two))
}

@InternalOpenCodeApi public fun printDiff(title: String, one: List<String>, two: List<String>) {
    val lineSeparator = "\n -\n"

    Log.d("✗ $title")
    Log.d("|----------------------------------------------------------------------")

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

    Log.d(lines)
    Log.d("|----------------------------------------------------------------------")
}

@InternalOpenCodeApi public fun printMatch(title: String) {
    Log.d("✓ $title")
}