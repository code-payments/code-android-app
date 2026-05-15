package com.flipcash.app.push

interface PushTokenProvider {
    suspend fun getToken(): String?
    suspend fun deleteToken()
}
