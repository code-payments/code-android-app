package com.getcode.utils

import com.getcode.ed25519kmp.Ed25519
import com.getcode.vendor.Base58

fun Ed25519.KeyPair.getPublicKeyBase58(): String = Base58.encode(publicKeyBytes)
