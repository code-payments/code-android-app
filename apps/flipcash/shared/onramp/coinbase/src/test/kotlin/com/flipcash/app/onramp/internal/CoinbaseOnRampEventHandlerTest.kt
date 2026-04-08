package com.flipcash.app.onramp.internal

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.TimeSource
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinbaseOnRampEventHandlerTest {

    private var successCount = 0
    private var cancelCount = 0
    private var autoClickCount = 0
    private var lastError: CoinbaseOnRampWebError? = null

    private val handler = CoinbaseOnRampEventHandler(
        startMark = TimeSource.Monotonic.markNow(),
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
    fun commitSuccessIsExplicitlySkipped() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_success"}""")
        assertEquals(0, successCount, "commit_success should not trigger onPaymentSuccess")
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
        assertIs<CoinbaseOnRampWebError.Internal>(lastError)
    }

    @Test
    fun loadErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.load_error","data":{"errorCode":"ERROR_CODE_GUEST_GOOGLE_PAY_ERROR"}}""")
        assertIs<CoinbaseOnRampWebError.GuestGooglePayError>(lastError)
    }

    @Test
    fun pollingErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.polling_error","data":{"errorCode":"ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED"}}""")
        assertIs<CoinbaseOnRampWebError.GuestTransactionBuyFailed>(lastError)
    }

    @Test
    fun sessionErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.session_error","data":{"errorCode":"ERROR_CODE_GUEST_CARD_NOT_DEBIT"}}""")
        assertIs<CoinbaseOnRampWebError.GuestCardNotDebit>(lastError)
    }

    @Test
    fun errorWithUnknownCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"SOME_NEW_ERROR"}}""")
        assertIs<CoinbaseOnRampWebError.Unknown>(lastError)
    }

    @Test
    fun errorWithMissingDataFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error"}""")
        assertIs<CoinbaseOnRampWebError.Unknown>(lastError)
    }

    @Test
    fun errorWithEmptyErrorCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":""}}""")
        assertIs<CoinbaseOnRampWebError.Unknown>(lastError)
    }

    // --- Data payload ---

    @Test
    fun errorCarriesJsonData() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"ERROR_CODE_INTERNAL","transactionId":"abc-123"}}""")
        val error = lastError
        assertIs<CoinbaseOnRampWebError.Internal>(error)
        assertNotNull(error.data)
        assertTrue(error.data!!.contains("abc-123"))
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
    fun fromErrorCodeAllKnownCodes() {
        val expected = mapOf(
            "ERROR_CODE_MISSING_TRANSACTION_UUID" to CoinbaseOnRampWebError.MissingTransactionUuid::class,
            "ERROR_CODE_GUEST_CARD_NOT_DEBIT" to CoinbaseOnRampWebError.GuestCardNotDebit::class,
            "ERROR_CODE_GUEST_GOOGLE_PAY_ERROR" to CoinbaseOnRampWebError.GuestGooglePayError::class,
            "ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED" to CoinbaseOnRampWebError.GuestTransactionBuyFailed::class,
            "ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED" to CoinbaseOnRampWebError.GuestTransactionSendFailed::class,
            "ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED" to CoinbaseOnRampWebError.GuestTransactionAvsValidationFailed::class,
            "ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED" to CoinbaseOnRampWebError.GuestTransactionTransactionFailed::class,
            "ERROR_CODE_INTERNAL" to CoinbaseOnRampWebError.Internal::class,
            "ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND" to CoinbaseOnRampWebError.GooglePayButtonNotFound::class,
        )

        for ((code, expectedType) in expected) {
            val result = CoinbaseOnRampWebError.fromErrorCode(code)
            assertTrue(expectedType.isInstance(result), "Failed for code: $code")
        }
    }

    @Test
    fun fromErrorCodeUnknownCodeReturnsUnknown() {
        assertIs<CoinbaseOnRampWebError.Unknown>(CoinbaseOnRampWebError.fromErrorCode("SOMETHING_NEW"))
    }

    @Test
    fun fromErrorCodeEmptyStringReturnsUnknown() {
        assertIs<CoinbaseOnRampWebError.Unknown>(CoinbaseOnRampWebError.fromErrorCode(""))
    }

    @Test
    fun fromErrorCodeCaseSensitive() {
        assertIs<CoinbaseOnRampWebError.Unknown>(CoinbaseOnRampWebError.fromErrorCode("error_code_internal"))
    }
}
