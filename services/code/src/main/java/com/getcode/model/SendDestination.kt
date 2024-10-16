package com.getcode.model

sealed class SendDestination {
    data class SendDestinationPublicKey(
        val publicKey: List<Byte>,
    ) : SendDestination()

    data class SendDestinationPhone(
        val phoneNumber: String
    ) : SendDestination()
}