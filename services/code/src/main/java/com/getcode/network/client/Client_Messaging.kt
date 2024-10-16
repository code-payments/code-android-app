package com.getcode.network.client

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Domain
import com.getcode.model.Fiat

suspend fun Client.sendRequestToLogin(
    domain: Domain,
    verifier: KeyPair,
    rendezvous: KeyPair
): Result<MessagingService.SendMessageResponse> {
    return messagingRepository.sendRequestToLogin(domain, verifier, rendezvous)
}

suspend fun Client.sendRequestToReceiveBill(
    destination: com.getcode.solana.keys.PublicKey,
    fiat: Fiat,
    rendezvous: KeyPair
): Result<MessagingService.SendMessageResponse> {
    return messagingRepository.sendRequestToReceiveBill(destination, fiat, rendezvous)
}

suspend fun Client.rejectLogin(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
    return messagingRepository.rejectLogin(rendezvous)
}