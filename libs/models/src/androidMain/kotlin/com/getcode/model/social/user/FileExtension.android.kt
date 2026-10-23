package com.getcode.model.social.user

import android.webkit.MimeTypeMap

internal actual fun fileExtensionFromUrl(url: String): String =
    MimeTypeMap.getFileExtensionFromUrl(url)
