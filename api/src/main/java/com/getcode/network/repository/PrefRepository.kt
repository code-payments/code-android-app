package com.getcode.network.repository

import com.getcode.db.Database
import com.getcode.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.CoroutineContext

class PrefRepository @Inject constructor(
    @Named("io") private val coroutineContext: CoroutineContext,
    ) {
    fun get(key: PrefsString): Flow<String> = flow {
        val db = Database.getInstance()
        db?.prefStringDao()?.get(key.value)?.collect { prefEntity ->
            if (prefEntity != null) {
                emit(prefEntity.value)
            }
        }
    }.flowOn(coroutineContext).distinctUntilChanged()


    fun get(key: PrefsBool): Flow<Boolean> = flow {
        val db = Database.getInstance()
        db?.prefBoolDao()?.get(key.value)?.collect { prefEntity ->
            if (prefEntity != null) {
                emit(prefEntity.value)
            }
        }
    }.flowOn(coroutineContext).distinctUntilChanged()

    fun get(key: String): Flow<String> = flow {
        val db = Database.getInstance()
        db?.prefStringDao()?.get(key)?.collect { prefEntity ->
            if (prefEntity != null) {
                emit(prefEntity.value)
            }
        }
    }.flowOn(coroutineContext).distinctUntilChanged()

    suspend fun getFirstOrDefault(key: PrefsString, default: String): String = withContext(coroutineContext) {
        val db = Database.getInstance() ?: return@withContext default
        db.prefStringDao().getMaybe(key.value)?.value ?: default
    }

    suspend fun getFirstOrDefault(key: PrefsBool, default: Boolean): Boolean = withContext(coroutineContext) {
        val db = Database.getInstance() ?: return@withContext default
        db.prefBoolDao().getMaybe(key.value)?.value ?: default
    }

    suspend fun getFirstOrDefault(key: String, default: Int): Long = withContext(coroutineContext) {
        val db = Database.getInstance() ?: return@withContext default.toLong()
        db.prefIntDao().getMaybe(key)?.value ?: default.toLong()
    }


    fun set(vararg list: Pair<PrefsString, String>) {
        CoroutineScope(coroutineContext).launch {
            list.forEach { pair ->
                Database.getInstance()?.prefStringDao()?.insert(PrefString(pair.first.value, pair.second))
            }
        }
    }

    fun set(key: PrefsString, value: String) {
        set(Pair(key, value))
    }

    fun set(key: String, value: Int) = set(key, value.toLong())

    fun set(key: String, value: Long) {
        CoroutineScope(coroutineContext).launch {
            Database.getInstance()?.prefIntDao()?.insert(PrefInt(key, value))
        }
    }

    fun set(key: PrefsBool, value: Boolean) {
        CoroutineScope(coroutineContext).launch {
            Database.getInstance()?.prefBoolDao()?.insert(PrefBool(key.value, value))
        }
    }

}