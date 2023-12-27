package com.getcode.model

import android.net.Uri
import androidx.core.net.toUri


data class Domain(
    val relationshipHost: String,
    val urlString: String
) { companion object {
        fun from(uri: Uri): Domain? {
            val url =  if (uri.scheme == null) uri.buildUpon().scheme("https").build() else uri

            val hostName = url.host
            val baseHost = baseDomain(hostName)

            if (!(hostName != null && baseHost != null)) {
               return null
            }

            return Domain(baseHost, uri.toString())
        }

        fun from(url: String?): Domain? {
            url ?: return null
            return from(url.toUri())
        }

    }
}

private fun baseDomain(hostname: String?): String? {
    val components = hostname?.split(".")?.takeIf { it.count() > 1  } ?: return null
    return components.takeLast(2).joinToString(".")
}