package com.getcode.network.repository

import com.getcode.db.Database
import com.getcode.model.*
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class PrefRepository @Inject constructor() {
    fun get(key: PrefsString): Flowable<String> {
        val db = Database.getInstance() ?: return Flowable.empty()
        return db.prefStringDao().get(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .distinctUntilChanged()
    }

    fun get(key: PrefsBool): Flowable<Boolean> {
        val db = Database.getInstance() ?: return Flowable.empty()
        return db.prefBoolDao().get(key.value)
            .subscribeOn(Schedulers.computation())
            .map { it.value }
            .distinctUntilChanged()
    }

    fun get(key: String): Flowable<Long> {
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

    fun set(vararg list: Pair<PrefsString, String>) {
        CoroutineScope(Dispatchers.IO).launch {
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
        CoroutineScope(Dispatchers.IO).launch {
            Database.getInstance()?.prefIntDao()?.insert(PrefInt(key, value))
        }
    }

    fun set(key: PrefsBool, value: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            Database.getInstance()?.prefBoolDao()?.insert(PrefBool(key.value, value))
        }
    }

}