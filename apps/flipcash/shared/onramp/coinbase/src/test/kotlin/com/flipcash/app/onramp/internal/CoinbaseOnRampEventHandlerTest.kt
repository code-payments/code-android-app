package com.flipcash.app.onramp.internal

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.TimeSource
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_ASSET_NOT_TRADABLE
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GOOGLE_PAY_BUTTON_NOT_FOUND
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_HARD_DECLINED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_INSUFFICIENT_BALANCE
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_NOT_DEBIT
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_PREPAID_DECLINED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_RISK_DECLINED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_CARD_SOFT_DECLINED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_GOOGLE_PAY_ERROR
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_GOOGLE_PAY_NOT_READY
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_GOOGLE_PAY_NOT_SUPPORTED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_INVALID_CARD
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_PERMISSION_DENIED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_REGION_MISMATCH
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_BUY_FAILED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_COUNT
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_LIMIT
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_SEND_FAILED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_GUEST_TRANSACTION_TRANSACTION_FAILED
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_INIT
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_INTERNAL
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_INVALID_BILLING_ADDRESS
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_INVALID_BILLING_NAME
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_INVALID_BILLING_ZIP
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError.Companion.CODE_MISSING_TRANSACTION_UUID
import com.getcode.utils.NotifiableError
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinbaseOnRampEventHandlerTest {

    private var successCount = 0
    private var cancelCount = 0
    private var autoClickCount = 0
    private var heartbeatCount = 0
    private var pauseWatchdogCount = 0
    private var lastError: CoinbaseOnRampWebError? = null

    private val handler = CoinbaseOnRampEventHandler(
        orderId = "test-order-id",
        startMark = TimeSource.Monotonic.markNow(),
        webViewVersion = "1.0.0",
        gmsVersion = "24.0.0",
        onPaymentSuccess = { successCount++ },
        onPaymentFailure = { lastError = it },
        onCancel = { cancelCount++ },
        onAutoClickGPay = { autoClickCount++ },
        onHeartbeat = { heartbeatCount++ },
        onPauseWatchdog = { pauseWatchdogCount++ },
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
        assertIs<CoinbaseOnRampWebError.InternalFailure.Internal>(lastError)
    }

    @Test
    fun loadErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.load_error","data":{"errorCode":"ERROR_CODE_GUEST_GOOGLE_PAY_ERROR"}}""")
        assertIs<CoinbaseOnRampWebError.TransactionFailed.GooglePayError>(lastError)
    }

    @Test
    fun pollingErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.polling_error","data":{"errorCode":"ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED"}}""")
        assertIs<CoinbaseOnRampWebError.CardDeclined.BuyFailed>(lastError)
    }

    @Test
    fun sessionErrorTriggersFailure() {
        handler.handleEvent("""{"eventName":"onramp_api.session_error","data":{"errorCode":"ERROR_CODE_GUEST_CARD_NOT_DEBIT"}}""")
        assertIs<CoinbaseOnRampWebError.GuestCardNotDebit>(lastError)
    }

    @Test
    fun errorWithUnknownCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"SOME_NEW_ERROR"}}""")
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(lastError)
    }

    @Test
    fun errorWithMissingDataFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error"}""")
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(lastError)
    }

    @Test
    fun errorWithEmptyErrorCodeFallsBackToUnknown() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":""}}""")
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(lastError)
    }

    // --- Data payload ---

    @Test
    fun errorCarriesJsonData() {
        handler.handleEvent("""{"eventName":"onramp_api.commit_error","data":{"errorCode":"ERROR_CODE_INTERNAL","transactionId":"abc-123"}}""")
        val error = lastError
        assertIs<CoinbaseOnRampWebError.InternalFailure.Internal>(error)
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

    // --- Heartbeat / watchdog ---

    @Test
    fun heartbeatFiredForStandardEvents() {
        val events = listOf(
            """{"eventName":"onramp_api.load_success"}""",
            """{"eventName":"onramp_api.polling_success"}""",
            """{"eventName":"onramp_api.cancel"}""",
            """{"eventName":"onramp_api.commit_success"}""",
            """{"eventName":"onramp_api.payment_authorized"}""",
            """{"eventName":"timing.gpay_button_clicked"}""",
        )
        events.forEachIndexed { index, event ->
            handler.handleEvent(event)
            assertEquals(index + 1, heartbeatCount, "heartbeat not fired for: $event")
        }
        assertEquals(0, pauseWatchdogCount)
    }

    @Test
    fun pendingPaymentAuthPausesWatchdog() {
        handler.handleEvent("""{"eventName":"onramp_api.pending_payment_auth"}""")
        assertEquals(0, heartbeatCount)
        assertEquals(1, pauseWatchdogCount)
    }

    @Test
    fun heartbeatNotFiredOnMalformedJson() {
        handler.handleEvent("not json")
        assertEquals(0, heartbeatCount)
        assertEquals(0, pauseWatchdogCount)
    }

    @Test
    fun heartbeatResumesAfterPendingPaymentAuth() {
        handler.handleEvent("""{"eventName":"onramp_api.pending_payment_auth"}""")
        assertEquals(1, pauseWatchdogCount)
        assertEquals(0, heartbeatCount)

        // Timing event arrives while paused — should stay paused
        handler.handleEvent("""{"eventName":"timing.gpay_button_clicked"}""")
        assertEquals(2, pauseWatchdogCount)
        assertEquals(0, heartbeatCount)

        handler.handleEvent("""{"eventName":"onramp_api.payment_authorized"}""")
        assertEquals(1, heartbeatCount)
        assertEquals(2, pauseWatchdogCount)
    }

    @Test
    fun timingEventsStayPausedDuringAuth() {
        handler.handleEvent("""{"eventName":"onramp_api.pending_payment_auth"}""")
        handler.handleEvent("""{"eventName":"timing.gpay_button_clicked"}""")
        assertEquals(0, heartbeatCount)
        assertEquals(2, pauseWatchdogCount)
    }
}

class CoinbaseOnRampWebErrorTest {

    @Test
    fun fromErrorCodeAllKnownCodes() {
        val expected = mapOf(
            CODE_MISSING_TRANSACTION_UUID to CoinbaseOnRampWebError.UnknownFailure.MissingTransactionUuid::class,
            CODE_GUEST_CARD_NOT_DEBIT to CoinbaseOnRampWebError.GuestCardNotDebit::class,
            CODE_GUEST_INVALID_CARD to CoinbaseOnRampWebError.GuestCardNotDebit::class,
            CODE_GUEST_GOOGLE_PAY_ERROR to CoinbaseOnRampWebError.TransactionFailed.GooglePayError::class,
            CODE_GUEST_TRANSACTION_BUY_FAILED to CoinbaseOnRampWebError.CardDeclined.BuyFailed::class,
            CODE_GUEST_TRANSACTION_SEND_FAILED to CoinbaseOnRampWebError.TransactionFailed.SendFailed::class,
            CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED to CoinbaseOnRampWebError.BillingAddressInvalid.AvsValidationFailed::class,
            CODE_GUEST_TRANSACTION_TRANSACTION_FAILED to CoinbaseOnRampWebError.TransactionFailed.ProcessingFailed::class,
            CODE_GUEST_REGION_MISMATCH to CoinbaseOnRampWebError.RegionNotSupported.RegionMismatch::class,
            CODE_GUEST_TRANSACTION_LIMIT to CoinbaseOnRampWebError.GuestWeeklyTransactionLimitReached::class,
            CODE_GUEST_TRANSACTION_COUNT to CoinbaseOnRampWebError.GuestTransactionMaxLimitReached::class,
            CODE_GUEST_CARD_RISK_DECLINED to CoinbaseOnRampWebError.GuestCardRiskDeclined::class,
            CODE_GUEST_PERMISSION_DENIED to CoinbaseOnRampWebError.GuestPermissionDenied::class,
            CODE_ASSET_NOT_TRADABLE to CoinbaseOnRampWebError.RegionNotSupported.AssetNotTradable::class,
            CODE_GUEST_GOOGLE_PAY_NOT_READY to CoinbaseOnRampWebError.GuestGooglePayNotReady::class,
            CODE_GUEST_GOOGLE_PAY_NOT_SUPPORTED to CoinbaseOnRampWebError.GuestGooglePayNotSupported::class,
            CODE_GUEST_CARD_SOFT_DECLINED to CoinbaseOnRampWebError.CardDeclined.Soft::class,
            CODE_GUEST_CARD_HARD_DECLINED to CoinbaseOnRampWebError.CardDeclined.Hard::class,
            CODE_GUEST_CARD_INSUFFICIENT_BALANCE to CoinbaseOnRampWebError.GuestCardInsufficientBalance::class,
            CODE_GUEST_CARD_PREPAID_DECLINED to CoinbaseOnRampWebError.GuestCardPrepaidDeclined::class,
            CODE_INVALID_BILLING_ZIP to CoinbaseOnRampWebError.BillingAddressInvalid.InvalidZip::class,
            CODE_INVALID_BILLING_ADDRESS to CoinbaseOnRampWebError.BillingAddressInvalid.InvalidAddress::class,
            CODE_INVALID_BILLING_NAME to CoinbaseOnRampWebError.InvalidBillingName::class,
            CODE_INIT to CoinbaseOnRampWebError.InternalFailure.InitError::class,
            CODE_INTERNAL to CoinbaseOnRampWebError.InternalFailure.Internal::class,
            CODE_GOOGLE_PAY_BUTTON_NOT_FOUND to CoinbaseOnRampWebError.InternalFailure.GooglePayButtonNotFound::class,
        )

        for ((code, expectedType) in expected) {
            val result = CoinbaseOnRampWebError.fromErrorCode(code)
            assertTrue(expectedType.isInstance(result), "Failed for code: $code")
        }
    }

    @Test
    fun fromErrorCodeUnknownCodeReturnsUnknown() {
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(CoinbaseOnRampWebError.fromErrorCode("SOMETHING_NEW"))
    }

    @Test
    fun fromErrorCodeEmptyStringReturnsUnknown() {
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(CoinbaseOnRampWebError.fromErrorCode(""))
    }

    @Test
    fun fromErrorCodeCaseSensitive() {
        assertIs<CoinbaseOnRampWebError.UnknownFailure.Unknown>(CoinbaseOnRampWebError.fromErrorCode("error_code_internal"))
    }

    @Test
    fun internalFailureGroupImplementsNotifiableError() {
        assertIs<NotifiableError>(CoinbaseOnRampWebError.InternalFailure.InitError())
        assertIs<NotifiableError>(CoinbaseOnRampWebError.InternalFailure.Internal())
        assertIs<NotifiableError>(CoinbaseOnRampWebError.InternalFailure.WebViewTimeout())
        assertIs<NotifiableError>(CoinbaseOnRampWebError.InternalFailure.GooglePayButtonNotFound())
    }

    @Test
    fun unknownFailureGroupImplementsNotifiableError() {
        assertIs<NotifiableError>(CoinbaseOnRampWebError.UnknownFailure.Unknown())
        assertIs<NotifiableError>(CoinbaseOnRampWebError.UnknownFailure.MissingTransactionUuid())
    }

    @Test
    fun guestRegionMismatchIsNotNotifiable() {
        val error: CoinbaseOnRampWebError = CoinbaseOnRampWebError.RegionNotSupported.RegionMismatch()
        assertFalse(error is NotifiableError)
    }

    @Test
    fun paymentSheetTimeoutIsNotNotifiable() {
        val error: CoinbaseOnRampWebError = CoinbaseOnRampWebError.PaymentSheetTimeout()
        assertFalse(error is NotifiableError)
    }
}
