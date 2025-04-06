package com.getcode.opencode.utils

inline fun <K, V> MutableMap<K, V>.getOrPutIfNonNull(key: K, defaultValue: () -> V?): V? {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        if (answer != null) {
            put(key, answer)
        }
        answer
    } else {
        value
    }
}