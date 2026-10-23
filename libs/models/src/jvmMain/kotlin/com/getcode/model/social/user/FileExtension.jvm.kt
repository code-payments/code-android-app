package com.getcode.model.social.user

private val FILENAME = Regex("[a-zA-Z_0-9.\\-()%]+")

/** Same rules as AOSP `MimeTypeMap.getFileExtensionFromUrl`. */
internal actual fun fileExtensionFromUrl(url: String): String {
    if (url.isEmpty()) return ""
    var path = url
    val fragment = path.lastIndexOf('#')
    if (fragment > 0) path = path.substring(0, fragment)
    val query = path.lastIndexOf('?')
    if (query > 0) path = path.substring(0, query)
    val filename = path.substring(path.lastIndexOf('/') + 1)
    if (filename.isEmpty() || !FILENAME.matches(filename)) return ""
    val dot = filename.lastIndexOf('.')
    return if (dot >= 0) filename.substring(dot + 1) else ""
}
