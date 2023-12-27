package com.getcode.model

import android.net.Uri
import androidx.core.net.toUri


data class Domain
@Throws(java.lang.IllegalArgumentException::class)
constructor(val uri: Uri) {

    @Throws(java.lang.IllegalArgumentException::class)
    constructor(url: String): this(url.toUri())

    var relationshipHost: String? = null
        private set
    var urlString: String? = null
        private set

    init {
        val url =  if (uri.scheme == null) uri.buildUpon().scheme("https").build() else uri

        val hostName = url.host
        val baseHost = baseDomain(hostName)

        if (!(hostName != null && baseHost != null)) {
            throw IllegalArgumentException()
        }

        urlString = uri.toString()
        relationshipHost = baseHost
    }
}

private fun baseDomain(hostname: String?): String? {
    val components = hostname?.split(".")?.takeIf { it.count() > 1  } ?: return null
    return components.takeLast(2).joinToString(".")
}