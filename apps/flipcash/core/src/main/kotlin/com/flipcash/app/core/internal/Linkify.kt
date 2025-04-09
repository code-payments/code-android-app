package com.flipcash.app.core.internal

import com.getcode.utils.urlEncode

internal object Linkify {
    fun cashLink(entropy: String): String = "https://cash.flipcash.com/c/#/e=${entropy}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
}