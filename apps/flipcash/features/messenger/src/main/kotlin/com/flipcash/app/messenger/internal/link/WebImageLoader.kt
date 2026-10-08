package com.flipcash.app.messenger.internal.link

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * The loader for a preview's picture, on [client] from [webImageClient] and nothing else: it shares
 * neither the app's loader nor that loader's client, which keeps cookies and follows any redirect.
 *
 * No disk cache. The default directory is the app loader's, and a picture of an outside page has
 * no business in a store the rest of the app reads.
 */
internal fun webPreviewImageLoader(context: Context, client: OkHttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .diskCache(null)
        .build()
