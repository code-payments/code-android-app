package com.getcode.opencode.pin

/**
 * Compares a target's [PinDump] with a pinned dump, keyed by case so a missing or extra case shows
 * up by name. A line is `section|case = value`, or `locale|tag|case|value` for the locale cases.
 */
internal object PinCheck {

    fun parse(lines: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val (name, value) = split(line)
            // Some cases share a name; number the repeats in order, which every target dumps the same way.
            var key = name
            var n = 1
            while (key in out) key = "$name #${++n}"
            out[key] = value
        }
        return out
    }

    private fun split(line: String): Pair<String, String> =
        if (line.startsWith("locale|")) {
            val at = line.lastIndexOf('|')
            line.substring(0, at) to line.substring(at + 1)
        } else {
            val at = line.indexOf(" = ")
            check(at > 0) { "not a pin line: $line" }
            line.substring(0, at) to line.substring(at + 3)
        }

    /**
     * Every case where [actual] differs from [expected], as `key: expected -> actual`. Cases
     * [actual] has no line for count as differences unless [skip] covers them.
     */
    fun differences(
        expected: Map<String, String>,
        actual: Map<String, String>,
        skip: (String) -> Boolean = { false },
    ): List<String> = buildList {
        for ((key, value) in expected) {
            if (skip(key)) continue
            val got = actual[key]
            if (got != value) add("$key: $value -> ${got ?: "(no case)"}")
        }
        for (key in actual.keys - expected.keys) add("$key: (not pinned) -> ${actual[key]}")
    }
}
