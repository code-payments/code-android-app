package com.flipcash.app.internal.debug

import timber.log.Timber

internal val FlipcashDebugTree = object : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        val elementTag = super.createStackElementTag(element)
            .orEmpty()
            .split("$")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString(" ")
            .replace("_", " ")

        val methodName = element.methodName
            .split("$")
            .firstOrNull()
            .orEmpty()

        return String.format(
            "%s | %s ",
            elementTag,
            methodName
        )
    }
}