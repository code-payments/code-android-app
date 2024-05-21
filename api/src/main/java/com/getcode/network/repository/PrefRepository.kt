package com.getcode.network.repository

import com.getcode.db.Database
import com.getcode.model.*
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import javax.inject.Inject



class PrefRepository @Inject constructor(): CoroutineScope by CoroutineScope(Dispatchers.IO) {

    suspend fun get(key: PrefsString, default: String): String {
        return observeOrDefault(key, default).firstOrNull() ?: default
    }

    suspend fun get(key: PrefsBool, default: Boolean): Boolean {
        return observeOrDefault(key, default).firstOrNull() ?: default
    }


    fun getFlowable(key: PrefsString): Flowable<String> {
        val db = Database.getInstance() ?: return Flowable.empty()
        return db.prefStringDao().get(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .distinctUntilChanged()
    }

    fun getFlowable(key: PrefsBool): Flowable<Boolean> {
        val db = Database.getInstance() ?: return Flowable.empty()
        return db.prefBoolDao().get(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .distinctUntilChanged()
    }

    fun observeOrDefault(key: PrefsBool, default: Boolean): Flow<Boolean> {
        return Database.isInit
            .asFlow()
            .map { Database.getInstance() }
            .flatMapLatest {
                it ?: return@flatMapLatest flowOf(default)
                it.prefBoolDao().observe(key.value).map { it?.value ?: default }
            }
            .flowOn(Dispatchers.IO)

    }

    fun observeOrDefault(key: PrefsString, default: String): Flow<String> {
        return Database.isInit
            .asFlow()
            .map { Database.getInstance() }
            .flatMapLatest {
                it ?: return@flatMapLatest flowOf(default).also { Timber.e("observe string ; DB not available") }
                it.prefStringDao().observe(key.value)
                    .map { it?.value ?: default }
            }
            .flowOn(Dispatchers.IO)
    }

    fun getFlowable(key: String): Flowable<Long> {
        val db = Database.getInstance() ?: return Flowable.empty()
        return db.prefIntDao().get(key)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .distinctUntilChanged()
    }

    fun getFirstOrDefault(key: PrefsString, default: String): Single<String> {
        val db = Database.getInstance() ?: return Single.just(default)
        return db.prefStringDao().getMaybe(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .defaultIfEmpty(default)
    }

    fun getFirstOrDefault(key: PrefsBool, default: Boolean): Single<Boolean> {
        val db = Database.getInstance() ?: return Single.just(default)
        return db.prefBoolDao().getMaybe(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .defaultIfEmpty(default)
    }

    fun getFirstOrDefault(key: String, default: Int): Single<Long> {
        val db = Database.getInstance() ?: return Single.just(default.toLong())
        return db.prefIntDao().getMaybe(key)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .defaultIfEmpty(default.toLong())
    }

    suspend fun set(vararg list: Pair<PrefsString, String>) {
        list.forEach { pair ->
            Database.getInstance()?.prefStringDao()?.insert(PrefString(pair.first.value, pair.second))
        }
    }

    fun set(key: PrefsString, value: String) = launch {
        set(key to value)
    }

    fun set(key: String, value: Int) = set(key, value.toLong())

    fun set(key: String, value: Long) {
        launch {
            Database.getInstance()?.prefIntDao()?.insert(PrefInt(key, value))
        }
    }

    fun set(key: PrefsBool, value: Boolean) {
        launch {
            runCatching {
                val db = Database.getInstance() ?: throw IllegalStateException("No DB")
                db.prefBoolDao().insert(PrefBool(key.value, value))
            }.onFailure { Timber.d(it.message) }.onSuccess { Timber.d("saved ${key.value} => $value") }
        }
    }

}