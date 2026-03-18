package com.flipcash.app.contact.verification

import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.core.AppRoute
import com.getcode.opencode.utils.base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object PhoneVerificationFlow {

    internal var key: String = ""
        private set

    internal var source: AppRoute? = null
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start(origin: AppRoute?) {
        source = origin
        key = Uuid.Companion.random().toString()
    }
}

object EmailVerificationFlow {

    internal var key: String = ""
        private set

    internal var clientData: String = ""
        private set
    internal var source: EmailDeeplinkOrigin? = null
        private set(value) {
            field = value
            val data = buildMap {
                put("origin", field?.serialize()?.base64)
            }

            clientData = Json.encodeToString(data)
        }

    private val _pendingCode = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)

    /** Observed by [EmailVerificationViewModel] to receive codes from deeplinks. */
    val pendingCode = _pendingCode.asSharedFlow()

    /**
     * Deliver a verification code to the already-open verification screen.
     * Returns true if the screen is active and will receive the code.
     */
    fun deliverCode(email: String, code: String): Boolean {
        if (_pendingCode.subscriptionCount.value == 0) return false
        return _pendingCode.tryEmit(email to code)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun start(source: EmailDeeplinkOrigin?) {
        this.source = source
        key = Uuid.Companion.random().toString()
    }
}