package com.getcode.model

sealed class StreamEvent(val id: String) {
    class SimulationEvent(
        id: String,
        val isFailed: Boolean,
        val rendezvousKey: ByteArray,
        val exchangeCurrency: String?,
        val exchangeRate: Double?,
        val amountNative: Double?,
        val kin: Kin?,
        val region: String?
    ) : StreamEvent(id)
}