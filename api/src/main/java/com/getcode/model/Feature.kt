package com.getcode.model

sealed interface Feature {
    val enabled: Boolean
    val available: Boolean
}

data class BuyModuleFeature(
    override val enabled: Boolean = false,
    override val available: Boolean = false, // server driven availability
): Feature

data class TipCardFeature(
    override val enabled: Boolean = false,
    override val available: Boolean = true, // always enabled
): Feature


data class RequestKinFeature(
    override val enabled: Boolean = false,
    override val available: Boolean = true, // always enabled
): Feature