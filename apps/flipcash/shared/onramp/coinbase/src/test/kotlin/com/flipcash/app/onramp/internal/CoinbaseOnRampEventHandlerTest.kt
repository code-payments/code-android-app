package com.flipcash.app.onramp.internal

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinbaseOnRampEventHandlerTest {

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    private var successCount = 0
    private var cancelCount = 0
    private var autoClickCount = 0
    private var lastError: CoinbaseOnRampWebError? = null

    private val handler = CoinbaseOnRampEventHandler(
        onPaymentSuccess = { successCount++ },
        onPaymentFailure = { lastError = it },
        onCancel = { cancelCount++ },
        onAutoClickGPay = { autoClickCount++ },
    )

    // --- Event routing ---

    @Test
    fun loadSuccessTriggersAutoClick() {
        handler.handleEvent("""{"eventName":"onramp_api.load_success"}""")
        assertEquals(1, autoClickCount)
    }

    @Test
    fun commitSuccessTriggersPaymentSuccess() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_success"}""")
        assertEquals(1, successCount)
    }

    @Test
    fun pollingSuccessTriggersPaymentSuccess() {
        handler.handleEvent("""{"eventName":"onramp_api.polling_success"}""")
        assertEquals(1, successCount)
    }

    @Test
    fun cancelTriggersOnCancel() {
        handler.handleEvent("""{"eventName":"onramp_api.cancel"}""")
        assertEquals(1, cancelCount)
    }

    // --- Error events ---

    @Test
    fun commitErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"ERROR_CODE_INTERNAL"}}""")
        assertEquals(CoinbaseOnRampWebError.ERROR_CODE_INTERNAL, lastError)
    }

    @Test
    fun loadErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.load_error","data":{"errorCode":"ERROR_CODE_GUEST_GOOGLE_PAY_ERROR"}}""")
        assertEquals(CoinbaseOnRampWebError.ERROR_CODE_GUEST_GOOGLE_PAY_ERROR, lastError)
    }

    @Test
    fun pollingErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.polling_error","data":{"errorCode":"ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED"}}""")
        assertEquals(CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED, lastError)
    }

    @Test
    fun sessionErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.session_error","data":{"errorCode":"ERROR_CODE_GUEST_CARD_NOT_DEBIT"}}""")
        assertEquals(CoinbaseOnRampWebError.ERROR_CODE_GUEST_CARD_NOT_DEBIT, lastError)
    }

    @Test
    fun errorWithUnknownCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"SOME_NEW_ERROR"}}""")
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, lastError)
    }

    @Test
    fun errorWithMissingDataFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error"}""")
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, lastError)
    }

    @Test
    fun errorWithEmptyErrorCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":""}}""")
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, lastError)
    }

    // --- Edge cases ---

    @Test
    fun invalidJsonDoesNotCrash() {
        handler.handleEvent("not json")
        assertEquals(0, successCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun unknownEventNameIsIgnored() {
        handler.handleEvent("""{"eventName":"onramp_api.unknown_event"}""")
        assertEquals(0, successCount)
        assertEquals(0, cancelCount)
        assertTrue(lastError == null)
    }

    @Test
    fun missingEventNameIsIgnored() {
        handler.handleEvent("""{"data":{"errorCode":"ERROR_CODE_INTERNAL"}}""")
        assertEquals(0, successCount)
        assertTrue(lastError == null)
    }
}

class CoinbaseOnRampWebErrorTest {

    @Test
    fun tryValueOfAllKnownCodes() {
        val expected = mapOf(
            "ERROR_CODE_MISSING_TRANSACTION_UUID" to CoinbaseOnRampWebError.ERROR_CODE_MISSING_TRANSACTION_UUID,
            "ERROR_CODE_GUEST_CARD_NOT_DEBIT" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_CARD_NOT_DEBIT,
            "ERROR_CODE_GUEST_GOOGLE_PAY_ERROR" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_GOOGLE_PAY_ERROR,
            "ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED,
            "ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED,
            "ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED,
            "ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED" to CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED,
            "ERROR_CODE_INTERNAL" to CoinbaseOnRampWebError.ERROR_CODE_INTERNAL,
            "ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND" to CoinbaseOnRampWebError.ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND,
        )

        for ((code, expectedError) in expected) {
            assertEquals(expectedError, CoinbaseOnRampWebError.tryValueOf(code), "Failed for code: $code")
        }
    }

    @Test
    fun tryValueOfUnknownCodeReturnsUnknown() {
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, CoinbaseOnRampWebError.tryValueOf("SOMETHING_NEW"))
    }

    @Test
    fun tryValueOfEmptyStringReturnsUnknown() {
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, CoinbaseOnRampWebError.tryValueOf(""))
    }

    @Test
    fun tryValueOfCaseSensitive() {
        assertEquals(CoinbaseOnRampWebError.UNKNOWN, CoinbaseOnRampWebError.tryValueOf("error_code_internal"))
    }
}
