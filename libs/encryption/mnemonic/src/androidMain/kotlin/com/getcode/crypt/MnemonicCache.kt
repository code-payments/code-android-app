package com.getcode.crypt

import android.content.Context

object MnemonicCache {
    val cachedCode: MnemonicCode = MnemonicCode

    fun init(context: Context) {
        // MnemonicCode no longer needs Android resources -- the wordlist is a compile-time
        // constant (see Bip39EnglishWordList). Kept so MnemonicCacheInitializer's startup
        // call still compiles.
    }

    val cache = mutableMapOf<Pair<List<String>, String>, ByteArray>()
}
