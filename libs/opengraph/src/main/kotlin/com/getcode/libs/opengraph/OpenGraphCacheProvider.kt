package com.getcode.libs.opengraph

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.getcode.libs.opengraph.cache.CacheProvider
import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.utils.base64
import com.getcode.utils.decodeBase64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class OpenGraphCacheProvider @Inject constructor(
    @ApplicationContext
    context: Context
): CacheProvider {

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storage = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("open-graph") }
    )

    override suspend fun get(url: String): OpenGraphResult? {
        return storage.data.map { prefs ->
            val result = prefs[stringPreferencesKey(url)] ?: return@map null
            Json.decodeFromString<OpenGraphResult>(result.decodeBase64().decodeToString())
        }.firstOrNull()
    }

    override suspend fun set(openGraphResult: OpenGraphResult, url: String) {
        dataScope.launch(Dispatchers.IO) {
            storage.edit { prefs ->
                prefs[stringPreferencesKey(url)] = Json.encodeToString(openGraphResult).encodeToByteArray().base64
            }
        }
    }
}