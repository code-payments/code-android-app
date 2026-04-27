package com.flipcash.app.updates.internal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flipcash.app.updates.ReleaseManifest
import com.flipcash.app.updates.ReleaseStage
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.updates.resolveStage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

private val Context.releaseStageDataStore by preferencesDataStore(name = "release-stage")

private val MANIFEST_KEY = stringPreferencesKey("release_manifest_json")

private const val MANIFEST_URL =
    "https://raw.githubusercontent.com/code-payments/code-android-app/code/cash/.well-known/release-manifest.json"

internal class GooglePlayReleaseStageProvider(private val context: Context) : ReleaseStageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    override var resolvedStage: ReleaseStage = ReleaseStage.Production
        private set

    override suspend fun loadCachedStage(versionCode: Int): ReleaseStage {
        val cached = context.releaseStageDataStore.data
            .map { prefs -> prefs[MANIFEST_KEY] }
            .firstOrNull()

        if (cached != null) {
            resolvedStage = resolveStage(cached, versionCode)
        }
        return resolvedStage
    }

    override suspend fun fetchAndCache(versionCode: Int) {
        try {
            val request = Request.Builder().url(MANIFEST_URL).build()
            val response = OkHttpClient().newCall(request).execute()
            if (!response.isSuccessful) return

            val body = response.body?.string() ?: return

            context.releaseStageDataStore.edit { prefs ->
                prefs[MANIFEST_KEY] = body
            }

            resolvedStage = resolveStage(body, versionCode)
        } catch (e: Exception) {
            Timber.d(e, "Failed to fetch release manifest")
        }
    }

    private fun resolveStage(manifestJson: String, versionCode: Int): ReleaseStage {
        return try {
            val manifest = json.decodeFromString<ReleaseManifest>(manifestJson)
            resolveStage(manifest, versionCode)
        } catch (e: Exception) {
            Timber.d(e, "Failed to parse release manifest")
            ReleaseStage.Production
        }
    }
}