package com.getcode.libs.opengraph.fetcher

import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.libs.opengraph.model.Proxy
import org.jsoup.Jsoup
import java.net.URI
import java.net.URL

class JsoupFetcher(
    private val timeout: Int? = DEFAULT_TIMEOUT,
    private val jsoupProxy: Proxy? = null,
    private val maxBodySize: Int? = null
) {
    fun call(url: String, agent: String): OpenGraphResult? {
        var image: String? = null
        var description: String? = null
        var title: String? = null
        var resultUrl: String? = null
        var siteName: String? = null
        var type: String? = null

        return try {
            val connection = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(agent)
                .referrer(REFERRER)
                .timeout(timeout ?: DEFAULT_TIMEOUT)
                .followRedirects(true)

            jsoupProxy?.let { connection.proxy(it.host, it.port) }
            maxBodySize?.let { connection.maxBodySize(it) }

            val response = connection.execute()
            val doc = response.parse()
            val ogTags = doc.select(DOC_SELECT_OGTAGS)

            ogTags.forEach { tag ->
                when (tag.attr(PROPERTY)) {
                    OG_IMAGE -> {
                        image = tag.attr(OPEN_GRAPH_KEY)
                    }
                    OG_DESCRIPTION -> {
                        description = tag.attr(OPEN_GRAPH_KEY)
                    }

                    OG_URL -> {
                        resultUrl = tag.attr(OPEN_GRAPH_KEY)
                    }

                    OG_TITLE -> {
                        title = tag.attr(OPEN_GRAPH_KEY)
                    }

                    OG_SITE_NAME -> {
                        siteName = tag.attr(OPEN_GRAPH_KEY)
                    }

                    OG_TYPE -> {
                        type = tag.attr(OPEN_GRAPH_KEY)
                    }
                }
            }

            if (title.isNullOrEmpty()) {
                title = doc.title()
            }

            if (description.isNullOrEmpty()) {
                val docSelection = doc.select(DOC_SELECT_DESCRIPTION)
                description = docSelection.firstOrNull()?.attr("content").orEmpty()
            }

            if (resultUrl.isNullOrEmpty()) {
                resultUrl = getBaseUrl(url)
            }

            OpenGraphResult(title, description, resultUrl, image, siteName, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val REFERRER = "http://flipchat.xyz"
        private const val DEFAULT_TIMEOUT = 60000

        private const val DOC_SELECT_OGTAGS = "meta[property^=og:]"
        private const val DOC_SELECT_DESCRIPTION = "meta[name=description]"

        private const val OPEN_GRAPH_KEY = "content"
        private const val PROPERTY = "property"

        private const val OG_IMAGE = "og:image"
        private const val OG_DESCRIPTION = "og:description"
        private const val OG_URL = "og:url"
        private const val OG_TITLE = "og:title"
        private const val OG_SITE_NAME = "og:site_name"
        private const val OG_TYPE = "og:type"
    }
}

fun checkNullParserResult(openGraphResult: OpenGraphResult?): Boolean {
    return (openGraphResult?.title.isNullOrEmpty() || openGraphResult?.title == "null") &&
            (openGraphResult?.description.isNullOrEmpty() || openGraphResult?.description == "null")
}

private fun getBaseUrl(urlString: String): String {
    val url: URL = URI.create(urlString).toURL()
    return url.protocol.toString() + "://" + url.authority + "/"
}
