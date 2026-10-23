package com.getcode.model

data class Currency(
    val code: String,
    val name: String,
    val resId: Int? = null,
    val symbol: String = "",
    val rate: Double = 0.0
) {
    companion object
}