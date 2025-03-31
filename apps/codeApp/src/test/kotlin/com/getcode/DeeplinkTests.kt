package com.getcode

import org.junit.Test

class DeeplinkTests {

    private val regex = Regex("^(login|payment|tip)?-?request-(modal|page)-(mobile|desktop)\$")
    
    private val loginPathRoutes = listOf(
        "codewallet://getcode.com/v1/elements/login-request-modal-mobile",
        "codewallet://getcode.com/v1/elements/login-request-page-mobile",
        "codewallet://getcode.com/v1/elements/login-request-modal-desktop",
        "codewallet://getcode.com/v1/elements/login-request-page-desktop"
    )

    private val paymentPathRoutes = listOf(
        "codewallet://getcode.com/v1/elements/payment-request-modal-mobile",
        "codewallet://getcode.com/v1/elements/payment-request-page-mobile",
        "codewallet://getcode.com/v1/elements/payment-request-modal-desktop",
        "codewallet://getcode.com/v1/elements/payment-request-page-desktop"
    )

    private val tipsPathRoutes = listOf(
        "codewallet://getcode.com/v1/elements/tip-request-modal-mobile",
        "codewallet://getcode.com/v1/elements/tip-request-page-mobile",
        "codewallet://getcode.com/v1/elements/tip-request-modal-desktop",
        "codewallet://getcode.com/v1/elements/tip-request-page-desktop"
    )

    @Test
    fun testSdkTriggers() {
        loginPathRoutes.map { it.substringAfterLast("/") }
            .onEach { assert(regex.matches(it)) }

        paymentPathRoutes.map { it.substringAfterLast("/") }
            .onEach { assert(regex.matches(it)) }

        tipsPathRoutes.map { it.substringAfterLast("/") }
            .onEach { assert(regex.matches(it)) }
    }
}