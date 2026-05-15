package com.flipcash.services.repository

import com.getcode.ed25519.Ed25519

interface SettingsRepository {
    suspend fun update(locale: String?, region: String?, owner: Ed25519.KeyPair): Result<Unit>
}