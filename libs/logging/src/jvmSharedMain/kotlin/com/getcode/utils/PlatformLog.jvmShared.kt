package com.getcode.utils

import java.net.URI

actual typealias LogFile = java.io.File

internal actual fun reportTraceError(error: Throwable) = ErrorUtils.handleError(error)

internal actual fun urlWithoutQuery(raw: String): String = try {
    val uri = URI(raw)
    val base = buildString {
        append(uri.scheme)
        append("://")
        if (uri.authority != null) append(uri.authority)
        if (uri.path != null) append(uri.path)
    }
    base
} catch (_: Exception) {
    raw.substringBefore('?').substringBefore('#')
}
