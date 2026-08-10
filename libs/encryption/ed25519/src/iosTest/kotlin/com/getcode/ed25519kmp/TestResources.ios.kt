package com.getcode.ed25519kmp

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

actual fun readTestResource(name: String): String {
    val nameWithoutExt = name.substringBeforeLast(".")
    val ext = name.substringAfterLast(".", "")
    val path = checkNotNull(
        NSBundle.mainBundle.pathForResource(nameWithoutExt, ext)
    ) { "Resource '$name' not found in bundle" }
    return checkNotNull(
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    ) { "Failed to read resource '$name' at $path" } as String
}
