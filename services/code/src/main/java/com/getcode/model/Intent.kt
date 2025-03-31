package com.getcode.model

sealed class Intent {
    data class Transfer(
        val id: List<Byte>,
        val amount: KinAmount,
        val source: List<Byte>,
        val destination: SendDestination
    ) : Intent()

    data class CreateTokenAccount(
        val id: List<Byte>,
        val owner: List<Byte>
    ) : Intent()
}