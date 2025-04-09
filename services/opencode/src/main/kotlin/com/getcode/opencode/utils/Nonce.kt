package com.getcode.opencode.utils

import kotlin.random.Random

val nonce
    get() = Random.nextBytes(10).toList()
