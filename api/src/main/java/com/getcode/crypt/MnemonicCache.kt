package com.getcode.crypt

import android.content.Context

object MnemonicCache {
    lateinit var cachedCode: MnemonicCode
        private set

    fun init(context: Context) {
        cachedCode = MnemonicCode(context.resources)
    }

    val cache = mutableMapOf<Pair<List<String>, String>, ByteArray>()
}