package com.getcode.network.client

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Domain
import com.getcode.model.Fiat
import com.getcode.solana.keys.PublicKey

suspend fun Client.sendRequestToLogin(
    domain: Domain,
    verifier: KeyPair,
    rendezvous: KeyPair
): Result<MessagingService.SendMessageResponse> {
    return messagingRepository.sendRequestToLogin(domain, verifier, rendezvous)
}

suspend fun Client.sendRequestToReceiveBill(
    destination: PublicKey,
    fiat: Fiat,
    rendezvous: KeyPair
): Result<MessagingService.SendMessageResponse> {
    return messagingRepository.sendRequestToReceiveBill(destination, fiat, rendezvous)
}