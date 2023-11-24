package com.getcode.model

import com.getcode.keys.Signature

data class ClientSignature(
    val transaction: Signature,
    val signature: Signature
)
