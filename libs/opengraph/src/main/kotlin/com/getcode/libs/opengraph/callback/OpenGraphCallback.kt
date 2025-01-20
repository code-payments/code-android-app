package com.getcode.libs.opengraph.callback

import com.getcode.libs.opengraph.model.OpenGraphResult

interface OpenGraphCallback {
    fun onResponse(result: OpenGraphResult)
    fun onError(error: String)
}