package com.flipcash.app.core.util

import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
}