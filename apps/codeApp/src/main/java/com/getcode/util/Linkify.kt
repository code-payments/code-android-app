package com.getcode.util

import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://cash.getcode.com/c/#/e=${entropy}"
    fun tipCard(username: String, platform: String): String = "https://tipcard.getcode.com/${platform}/${username}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
}