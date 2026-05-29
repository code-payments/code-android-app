package com.flipcash.app.updates

import kotlinx.serialization.Serializable

@Serializable
data class TrackInfo(
    val versionCode: Int,
    val versionName: String? = null,
)

@Serializable
data class ReleaseManifest(
    val updated: String = "",
    val tracks: Tracks = Tracks(),
) {
    @Serializable
    data class Tracks(
        val production: TrackInfo? = null,
        val beta: TrackInfo? = null,
        val alpha: TrackInfo? = null,
        val internal: TrackInfo? = null,
    )
}
