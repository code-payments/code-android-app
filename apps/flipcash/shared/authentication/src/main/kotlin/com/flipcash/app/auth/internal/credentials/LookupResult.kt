package com.flipcash.app.auth.internal.credentials

sealed interface LookupResult {
    data object NoAccountFound : LookupResult
    data class TemporaryAccountCreated(val entropy: String, val seenAccessKey: Boolean) : LookupResult
    data class ExistingAccountFound(val entropy: String) : LookupResult
}