package com.getcode.model

import android.net.Uri
import androidx.core.net.toUri

class Domain private constructor(
    val relationshipHost: String,
    val urlString: String
) { companion object {
        fun from(uri: Uri, supportSubdomains: Boolean = false): Domain? {
            val url =  if (uri.scheme == null)
                Uri.Builder()
                    .scheme("https")
                    .authority(uri.path)
                    .build()
            else uri

            val hostName = url.host
            val baseHost = baseDomain(hostName, supportSubdomains)

            if (!(!hostName.isNullOrEmpty() && !baseHost.isNullOrEmpty())) {
               return null
            }

            return Domain(baseHost, uri.toString())
        }

        fun from(url: String?, supportSubdomains: Boolean = false): Domain? {
            url ?: return null
            return from(url.toUri(), supportSubdomains)
        }

    }
}

private fun baseDomain(hostname: String?, supportSubdomains: Boolean): String? {
    val components = hostname?.split(".")?.takeIf { it.count() > 1  } ?: return null
    //  1     2     3
    // app.getcode.com
    //
    //    1     2
    // getcode.com
    //
    val componentCount = if (supportSubdomains) 3 else 2
    return components.takeLast(componentCount).joinToString(".")
}