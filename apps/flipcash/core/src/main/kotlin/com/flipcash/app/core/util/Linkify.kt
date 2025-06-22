package com.flipcash.app.core.util

import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"
    fun pool(entropy: String): String = "https://fun.flipcash.com/pool/#/e=${entropy}"
    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
}