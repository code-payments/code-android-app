package com.flipcash.app.core.util

import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.base58
import com.getcode.utils.urlEncode

object Linkify {
    fun cashLink(entropy: String): String = "https://send.flipcash.com/c/#/e=${entropy}"
    fun download(shareRef: String): String = "https://flipcash.com/download?r=${shareRef}"
    fun tweet(message: String): String = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
    fun tokenInfo(token: Token): String = "https://app.flipcash.com/token/${token.address.base58()}"
}