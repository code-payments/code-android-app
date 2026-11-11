package com.getcode.crypt

object MnemonicCache {
    val cachedCode: MnemonicCode = MnemonicCode

    val cache = mutableMapOf<Pair<List<String>, String>, ByteArray>()
}
