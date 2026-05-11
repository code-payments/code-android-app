package com.flipcash.app.updates

interface ReleaseStageProvider {
    val resolvedStage: ReleaseStage?
    suspend fun loadCachedStage(versionCode: Int): ReleaseStage?
    suspend fun fetchAndCache(versionCode: Int)
}

internal fun resolveStage(manifest: ReleaseManifest, versionCode: Int): ReleaseStage {
    val tracks = manifest.tracks
    return when (versionCode) {
        tracks.production?.versionCode -> ReleaseStage.Production
        tracks.beta?.versionCode -> ReleaseStage.Beta
        tracks.alpha?.versionCode -> ReleaseStage.Alpha
        tracks.internal?.versionCode -> ReleaseStage.Internal
        else -> {
            val prodCode = tracks.production?.versionCode
            if (prodCode != null && versionCode > prodCode) ReleaseStage.Internal else ReleaseStage.Production
        }
    }
}

enum class ReleaseStage {
    Production,
    Alpha,
    Beta,
    Internal,
    ;
}


