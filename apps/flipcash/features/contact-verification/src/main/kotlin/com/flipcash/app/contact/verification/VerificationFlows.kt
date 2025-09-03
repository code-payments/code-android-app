package com.flipcash.app.contact.verification

import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.core.AppRoute
import com.getcode.opencode.utils.base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @OptIn(ExperimentalUuidApi::class)
    fun start(source: EmailDeeplinkOrigin?) {
        this.source = source
        key = Uuid.Companion.random().toString()
    }
}