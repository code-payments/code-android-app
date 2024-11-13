package com.getcode.services.model

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID

data class EcdsaTuple(
    val algorithm: KeyPair?,
    val id: ID?
)

typealias EcdsaTupleQuery = () -> EcdsaTuple