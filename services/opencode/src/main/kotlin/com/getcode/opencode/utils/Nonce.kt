package com.getcode.opencode.utils

import kotlin.random.Random

val nonce
    get() = Random.nextBytes(11).toList()
