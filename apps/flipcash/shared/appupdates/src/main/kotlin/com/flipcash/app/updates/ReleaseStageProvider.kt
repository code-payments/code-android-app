package com.flipcash.app.updates

interface ReleaseStageProvider {
    val resolvedStage: ReleaseStage
    suspend fun loadCachedStage(versionCode: Int): ReleaseStage
    suspend fun fetchAndCache(versionCode: Int)
}

internal fun resolveStage(manifest: ReleaseManifest, versionCode: Int): ReleaseStage {
    val tracks = manifest.tracks
    return when (versionCode) {
        tracks.internal?.versionCode -> ReleaseStage.Internal
        tracks.alpha?.versionCode -> ReleaseStage.Alpha
        tracks.beta?.versionCode -> ReleaseStage.Beta
        tracks.production?.versionCode -> ReleaseStage.Production
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


