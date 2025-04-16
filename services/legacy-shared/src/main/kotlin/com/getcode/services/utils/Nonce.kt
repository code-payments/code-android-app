package com.getcode.services.utils

import kotlin.random.Random

val nonce
    get() = Random.nextBytes(11).toList()
