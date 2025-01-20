package com.getcode.libs.opengraph.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenGraphResult(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val image: String? = null,
    val siteName: String? = null,
    val type: String? = null
)
