package com.flipcash.app.tokens

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object BuySellFlow {

    internal var key: String = ""
        private set

    var isForNeededFunds: Boolean = false
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start(forNeededFunds: Boolean) {
        key = Uuid.random().toString()
        isForNeededFunds = forNeededFunds
    }
}