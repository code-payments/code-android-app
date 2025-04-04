package com.getcode.opencode.domain.messaging

import com.getcode.ed25519.Ed25519.KeyPair

sealed interface KeepAliveRequest {
    data class OpenStreamRequest(val rendezvous: KeyPair): KeepAliveRequest
    data class ClientPong(val timestamp: Long): KeepAliveRequest
}