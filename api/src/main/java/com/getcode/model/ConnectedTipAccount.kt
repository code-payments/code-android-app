package com.getcode.model

sealed interface ConnectedTipAccount {
    val platform: String
    val username: String
}