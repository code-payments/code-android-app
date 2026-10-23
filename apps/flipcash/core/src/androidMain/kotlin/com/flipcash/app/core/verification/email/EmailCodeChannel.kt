package com.flipcash.app.core.verification.email

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailCodeChannel @Inject constructor() {
    private val _pendingCode = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val pendingCode: SharedFlow<Pair<String, String>> = _pendingCode.asSharedFlow()

    fun deliverCode(email: String, code: String): Boolean {
        if (_pendingCode.subscriptionCount.value == 0) return false
        return _pendingCode.tryEmit(email to code)
    }
}
