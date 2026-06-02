package com.flipcash.app.auth.internal.credentials

import com.flipcash.services.user.AuthState

sealed interface LookupResult {
    data object NoAccountFound : LookupResult
    data class TemporaryAccountCreated(val entropy: String, val resumePoint: AuthState.ResumePoint) : LookupResult
    data class ExistingAccountFound(val entropy: String) : LookupResult
}