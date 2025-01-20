package com.getcode.libs.opengraph

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.libs.opengraph.cache.CacheProvider
import com.getcode.libs.opengraph.callback.OpenGraphCallback
import com.getcode.libs.opengraph.fetcher.JsoupFetcher
import com.getcode.libs.opengraph.fetcher.checkNullParserResult
import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.libs.opengraph.model.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

val LocalOpenGraphParser = staticCompositionLocalOf<OpenGraphParser?> { null }

class OpenGraphParser @Inject constructor(
    private val showNullOnEmpty: Boolean = false,
    private val cacheProvider: CacheProvider? = null,
    timeout: Int? = null,
    proxy: Proxy? = null,
    maxBodySize: Int? = null
) {
    private val fetcher = JsoupFetcher(timeout, proxy, maxBodySize)

    fun parse(url: String, callback: OpenGraphCallback) {
        ParseLink(url, callback).parse()
    }

    inner class ParseLink(private val url: String, private val callback: OpenGraphCallback) : CoroutineScope {
        private val job: Job = Job()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        fun parse() = launch {
            val result = fetchContent(url, callback)
            result?.let {
                callback.onResponse(it)
            }
        }
    }
    private suspend fun fetchContent(url: String, callback: OpenGraphCallback) = withContext(Dispatchers.IO) {
        var validatedUrl = url
        if (!validatedUrl.contains("http")) {
            validatedUrl = "http://$validatedUrl"
        }

        cacheProvider?.get(url)?.let {
            return@withContext it
        }

        var openGraphResult: OpenGraphResult? = null
        AGENTS.forEach {
            openGraphResult = fetcher.call(validatedUrl, it)
            val isResultNull = checkNullParserResult(openGraphResult)
            if (!isResultNull) {
                openGraphResult?.let { cacheProvider?.set(it, url) }
                return@withContext openGraphResult
            }
        }

        if (checkNullParserResult(openGraphResult)) {
            launch(Dispatchers.Main) {
                callback.onError("Null or empty response from the server")
            }
            return@withContext null
        }

        openGraphResult?.let { cacheProvider?.set(it, url) }

        return@withContext openGraphResult
    }

    companion object {
        private val AGENTS = arrayOf(
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "Mozilla",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36",
            "WhatsApp/2.19.81 A",
            "facebookexternalhit/1.1",
            "facebookcatalog/1.0"
        )
    }

}