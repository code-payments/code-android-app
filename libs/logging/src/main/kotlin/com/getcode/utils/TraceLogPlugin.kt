package com.getcode.utils

fun interface TraceLogPlugin {
    /** Transform a log line before it's written to file. Return null to drop the line. */
    fun process(line: String): String?
}
