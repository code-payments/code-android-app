package com.getcode.view.main.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.model.ScreenModel
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.models.ScannableKikCode
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.crypt.MnemonicPhrase
import com.getcode.db.Database
import com.getcode.manager.*
import com.getcode.model.*
import com.getcode.model.Currency
import com.getcode.models.Bill
import com.getcode.models.BillState
import com.getcode.models.BillToast
import com.getcode.models.PaymentConfirmation
import com.getcode.models.PaymentState
import com.getcode.models.Valuation
import com.getcode.models.amountFloored
import com.getcode.network.BalanceController
import com.getcode.network.client.*
import com.getcode.network.repository.*
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.utils.ErrorUtils
import com.getcode.util.formatted
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.showNetworkError
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.NetworkUtils
import com.getcode.vendor.Base58
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.BaseViewModel
import com.getcode.view.camera.CameraController
import com.getcode.view.camera.LegacyCameraController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface PresentationStyle {
    data object Hidden : PresentationStyle
    sealed interface Visible

    data object Pop : PresentationStyle, Visible
    data object Slide : PresentationStyle, Visible
}

data class HomeUiModel(
    val isCameraPermissionGranted: Boolean? = null,
    val vibrateOnScan: Boolean = false,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val isCameraScanEnabled: Boolean = true,
    val selectedBottomSheet: HomeBottomSheet? = null,
    val presentationStyle: PresentationStyle = PresentationStyle.Hidden,
    val billState: BillState = BillState(null, false, null, null, null, false),
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val isDeepLinkHandled: Boolean = false,
)

enum class RestrictionType {
    ACCESS_EXPIRED,
    FORCE_UPGRADE,
    TIMELOCK_UNLOCKED
}

@SuppressLint("CheckResult")
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val client: Client,
    private val sendTransactionRepository: SendTransactionRepository,
    private val receiveTransactionRepository: ReceiveTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val balanceController: BalanceController,
    private val prefRepository: PrefRepository,
    private val analyticsManager: AnalyticsManager,
    private val authManager: AuthManager,
    private val networkUtils: NetworkUtils,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator,
    private val currencyUtils: CurrencyUtils,
) : BaseViewModel(resources), ScreenModel {
    val uiFlow = MutableStateFlow(HomeUiModel())
    private var billDismissTimer: TimerTask? = null
    private var sheetDismissTimer: TimerTask? = null
    private var cameraStarted = false
    private var scanPreviewBufferDisposable: Disposable? = null
    private var sendTransactionDisposable: Disposable? = null

    init {
        Database.isInit
            .flatMap { prefRepository.get(PrefsBool.IS_DEBUG_DISPLAY_ERRORS) }
            .subscribe(ErrorUtils::setDisplayErrors)

        StatusRepository().getIsUpgradeRequired(BuildConfig.VERSION_CODE)
            .subscribeOn(Schedulers.computation())
            .subscribe { isUpgradeRequired ->
                if (isUpgradeRequired) {
                    uiFlow.update { m -> m.copy(restrictionType = if (isUpgradeRequired) RestrictionType.FORCE_UPGRADE else null) }
                }
            }

        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_SCAN_TIMES, false)
            .flowOn(Dispatchers.IO)
            .onEach { log ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(logScanTimes = log)
                    }
                }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, false)
            .flowOn(Dispatchers.IO)
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(vibrateOnScan = enabled)
                    }
                }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_NETWORK_NO_CONNECTION, false)
            .flowOn(Dispatchers.IO)
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(showNetworkOffline = enabled)
                    }
                }
            }.launchIn(viewModelScope)

        CoroutineScope(Dispatchers.IO).launch {
            SessionManager.authState
                .distinctUntilChangedBy { it?.isTimelockUnlocked }
                .collectLatest {
                    it?.let { state ->
                        if (state.isTimelockUnlocked) {
                            uiFlow.update { m -> m.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) }
                        }
                    }
                }
        }
    }

    fun onCameraPermissionChanged(isGranted: Boolean) {
        uiFlow.update { it.copy(isCameraPermissionGranted = isGranted) }
    }

    fun showBill(
        bill: Bill,
        vibrate: Boolean = false
    ) {
        val amountFloor = bill.amountFloored
        if (amountFloor.fiat == 0.0 || bill.amount.kin.toKinTruncatingLong() == 0L) return
        val owner = SessionManager.getKeyPair() ?: return

        if (!networkUtils.isAvailable()) {
            return ErrorUtils.showNetworkError(resources)
        }

        val organizer = SessionManager.getOrganizer() ?: return

        // Don't show the remote send and cancel buttons for first kin
        when (bill) {
            is Bill.Cash -> {
                if (bill.kind == Bill.Kind.firstKin) {
                    uiFlow.update {
                        it.copy(
                            billState = it.billState.copy(
                                hideBillButtons = true
                            )
                        )
                    }
                }
            }

            else -> Unit
        }

        // this should not be in the view model
        sendTransactionDisposable?.dispose()
        sendTransactionRepository.init(amount = amountFloor, owner = owner)
        sendTransactionDisposable =
            sendTransactionRepository.startTransaction(organizer)
                .flatMapCompletable {
                    Completable.concatArray(
                        balanceController.fetchBalance(),
                        client.fetchLimits(isForce = true)
                    )
                }
                .subscribe({
                    cancelSend(PresentationStyle.Pop)
                    vibrator.vibrate()
                }, {
                    ErrorUtils.handleError(it)
                    cancelSend(style = PresentationStyle.Slide)
                })

        presentSend(sendTransactionRepository.payloadData, bill, vibrate)
    }

    private fun presentSend(data: List<Byte>, bill: Bill, isVibrate: Boolean = false) =
        viewModelScope.launch {
            billDismissTimer?.cancel()
            billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
                cancelSend()
                analyticsManager.billTimeoutReached(
                    bill.amount.kin,
                    bill.amount.rate.currency,
                    AnalyticsManager.BillPresentationStyle.Slide
                )
            }

            if (bill.didReceive) {
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        val billState = it.billState
                        it.copy(
                            billState = billState.copy(
                                valuation = Valuation(
                                    bill.amount
                                ),
                                showToast = bill.didReceive
                            )
                        )
                    }
                }
            }

            val style: PresentationStyle =
                if (bill.didReceive) PresentationStyle.Pop else PresentationStyle.Slide

            withContext(Dispatchers.Main) {
                uiFlow.update {
                    val billState = it.billState
                    it.copy(
                        presentationStyle = style,
                        billState = billState.copy(
                            bill = Bill.Cash(
                                data = data,
                                amount = bill.amount,
                                didReceive = bill.didReceive
                            ),
                            showToast = bill.didReceive
                        )
                    )
                }
            }

            if (style is PresentationStyle.Visible) {
                analyticsManager.billShown(
                    bill.amountFloored.kin,
                    bill.amountFloored.rate.currency,
                    when (style) {
                        PresentationStyle.Pop -> AnalyticsManager.BillPresentationStyle.Pop
                        PresentationStyle.Slide -> AnalyticsManager.BillPresentationStyle.Slide
                    }
                )
            }

            if (isVibrate) {
                vibrator.vibrate()
            }
        }

    fun canSwipeBill() = uiFlow.value.billState.canSwipeToDismiss

    fun cancelSend(style: PresentationStyle = PresentationStyle.Slide) {
        Timber.d("cancelsend")
        billDismissTimer?.cancel()
        sendTransactionDisposable?.dispose()
        BottomBarManager.clearByType(BottomBarManager.BottomBarMessageType.REMOTE_SEND)

        val shown = showToastIfNeeded(style)

        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Hidden,
                billState = it.billState.copy(
                    bill = null,
                    valuation = null,
                    hideBillButtons = false,
                )
            )
        }

        viewModelScope.launch {
            if (shown) {
                delay(5.seconds.inWholeMilliseconds)
            }
            withContext(Dispatchers.Main) {
                uiFlow.update {
                    it.copy(
                        billState = it.billState.copy(showToast = false)
                    )
                }
            }
        }
    }

    private fun showToastIfNeeded(style: PresentationStyle): Boolean {
        val billState = uiFlow.value.billState
        val bill = billState.bill ?: return false

        if (style is PresentationStyle.Pop || billState.showToast) {
            showToast(
                amount = bill.metadata.kinAmount,
                isDeposit = when (style) {
                    PresentationStyle.Slide -> true
                    PresentationStyle.Pop -> false
                    else -> false
                }
            )

            return true
        }

        return false
    }

    private fun showToast(
        amount: KinAmount,
        isDeposit: Boolean = false
    ) {
        if (amount.kin.toKinTruncatingLong() == 0L) {
            uiFlow.update { uiModel ->
                val billState = uiModel.billState
                uiModel.copy(
                    billState = billState.copy(
                        toast = null
                    )
                )
            }
            return
        }

        uiFlow.update {
            it.copy(
                billState = it.billState.copy(
                    showToast = true,
                    toast = BillToast(amount = amount, isDeposit = isDeposit)
                )
            )
        }

        Timer().schedule(5.seconds.inWholeMilliseconds) {
            uiFlow.update { uiModel ->
                val billState = uiModel.billState
                uiModel.copy(
                    billState = billState.copy(
                        showToast = false
                    )
                )
            }
            // wait for animation to run
            Timer().schedule(500.milliseconds.inWholeMilliseconds) {
                uiFlow.update {
                        uiModel ->
                    val billState = uiModel.billState
                    uiModel.copy(
                        billState = billState.copy(
                            toast = null
                        )
                    )
                }
            }
        }
    }

    private fun onCodeScan(payload: ByteArray) {
        if (DEBUG_SCAN_TIMES) {
            Timber.tag("codescan").d("start")
            scanProcessingTime = System.currentTimeMillis()
        }

        if (uiFlow.value.vibrateOnScan) {
            vibrator.tick()
        }

        val organizer = SessionManager.getOrganizer() ?: return

        val codePayload = CodePayload.fromList(payload.toList())

        if (scannedRendezvous.contains(codePayload.rendezvous.publicKey)) {
            Timber.d("Nonce previously received: ${codePayload.nonce.hexEncodedString()}")
            return
        }

        scannedRendezvous.add(codePayload.rendezvous.publicKey)

        if (!networkUtils.isAvailable()) {
            scannedRendezvous.remove(codePayload.rendezvous.publicKey)
            return ErrorUtils.showNetworkError(resources)
        }

        when (codePayload.kind) {
            Kind.Cash,
            Kind.GiftCard -> attemptReceive(organizer, codePayload)

            Kind.RequestPayment -> attemptPayment(codePayload)
        }
    }

    private fun attemptPayment(payload: CodePayload) {
        val request = paymentRepository.attemptRequest(payload) ?: return
        BottomBarManager.clear()

        presentRequest(request)
    }

    private fun presentRequest(request: Request) {
        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Pop,
                billState = it.billState.copy(
                    bill = Bill.Payment(request = request),
                    paymentConfirmation = PaymentConfirmation(
                        state = PaymentState.AwaitingConfirmation,
                        request.payload,
                        requestedAmount = request.amount
                    ),
                    hideBillButtons = true
                )
            )
        }

        if (DEBUG_SCAN_TIMES) {
            Timber.tag("codescan").d("scan processing took ${System.currentTimeMillis() - scanProcessingTime}")
            scanProcessingTime = 0
        }


        // vibrate with every payment request presentation (regardless of debug setting)
        vibrator.vibrate()
    }

    fun completePayment() = viewModelScope.launch {
        // keep bill active while sending
        billDismissTimer?.cancel()

        val paymentConfirmation = uiFlow.value.billState.paymentConfirmation ?: return@launch
        withContext(Dispatchers.Main) {
            uiFlow.update {
                val billState = it.billState
                it.copy(
                    billState = billState.copy(
                        paymentConfirmation = paymentConfirmation.copy(state = PaymentState.Sending)
                    ),
                )
            }
        }

        runCatching {
            paymentRepository.completePayment(
                paymentConfirmation.requestedAmount,
                paymentConfirmation.payload.rendezvous
            )
        }
            .onSuccess {
                showToast(paymentConfirmation.requestedAmount, false)

                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        val billState = it.billState
                        val confirmation = it.billState.paymentConfirmation ?: return@update it

                        it.copy(
                            billState = billState.copy(
                                paymentConfirmation = confirmation.copy(state = PaymentState.Sent),
                            ),
                        )
                    }
                }

                delay(500)
                uiFlow.update {
                    it.copy(
                        presentationStyle = PresentationStyle.Hidden,
                        billState = it.billState.copy(
                            bill = null,
                            paymentConfirmation = null,
                            valuation = null,
                            hideBillButtons = false,
                        )
                    )
                }
            }
            .onFailure { error ->
                error.printStackTrace()
                TopBarManager.showMessage(
                    "Payment Failed",
                    "This payment request could not be paid at this time. Please try again later."
                )
                uiFlow.update { uiModel ->
                    uiModel.copy(
                        presentationStyle = PresentationStyle.Hidden,
                        billState = uiModel.billState.copy(
                            bill = null,
                            showToast = false,
                            paymentConfirmation = null,
                            toast = null,
                            valuation = null,
                            hideBillButtons = false,
                        )
                    )
                }
            }
    }

    fun cancelPayment(rejected: Boolean, ignoreRedirect: Boolean = false) {
        val bill = uiFlow.value.billState.bill as? Bill.Payment ?: return
        val amount = bill.request.amount

        // TODO: analytics

        if (rejected) {
            if (!ignoreRedirect) {
                // TODO: handle cancelURL
            }
        } else {
            showToast(amount, isDeposit = false)

            if (!ignoreRedirect) {
                // TODO: handle successURL
            }
        }

        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Hidden,
                billState = it.billState.copy(
                    bill = null,
                    showToast = false,
                    paymentConfirmation = null,
                    toast = null,
                    valuation = null,
                    hideBillButtons = false,
                )
            )
        }
    }

    fun rejectPayment(ignoreRedirect: Boolean = false) {
        cancelPayment(true, ignoreRedirect)
        val payload = uiFlow.value.billState.paymentConfirmation?.payload ?: return
        paymentRepository.rejectPayment(payload)
    }

    @SuppressLint("CheckResult")
    private fun attemptReceive(organizer: Organizer, payload: CodePayload) {
        analyticsManager.grabStart()
        receiveTransactionRepository.start(organizer, payload.rendezvous)
            .doOnNext { metadata ->
                Timber.d("metadata=$metadata")
                val kinAmount = when (metadata) {
                    is IntentMetadata.SendPrivatePayment -> metadata.metadata.amount
                    is IntentMetadata.ReceivePaymentsPublicly -> metadata.metadata.amount
                    else -> return@doOnNext
                }

                analyticsManager.grab(kin = kinAmount.kin, currencyCode = kinAmount.rate.currency)

                val exchangeCurrency = kinAmount.rate.currency.name
                val exchangeRate = kinAmount.rate.fx
                val amountNative = kinAmount.kin.toKinValueDouble()

                Timber.i(
                    "StartTransaction $exchangeCurrency $exchangeRate x $amountNative = (${exchangeRate * amountNative})"
                )

                BottomBarManager.clear()

                showBill(
                    Bill.Cash(kinAmount, didReceive = true),
                    vibrate = true
                )
                if (DEBUG_SCAN_TIMES) {
                    Timber.tag("codescan").d("scan processing took ${System.currentTimeMillis() - scanProcessingTime}")
                    scanProcessingTime = 0
                }
            }
            .flatMapCompletable {
                Completable.concatArray(
                    balanceController.fetchBalance(),
                    client.fetchLimits(isForce = true)
                )
            }
            .subscribe({ }, {
                scannedRendezvous.remove(payload.rendezvous.publicKey)
                ErrorUtils.handleError(it)
            })
    }

    fun onCashLinkGrabStart() {
        analyticsManager.cashLinkGrabStart()
    }

    fun startScan(view: KikCodeScannerView) {
        if (cameraStarted) {
            return
        }
        Timber.i("startScan")
        uiFlow.update { it.copy(isCameraScanEnabled = true) }

        val scanner = KikCodeScannerImpl()

        scanPreviewBufferDisposable?.dispose()
        scanPreviewBufferDisposable = scanPreviewBuffer(view, scanner)
            .subscribe({}, ErrorUtils::handleError)
        cameraStarted = true
    }

    fun stopScan() {
        uiFlow.value = uiFlow.value.copy(isCameraScanEnabled = false)
        cameraStarted = false
    }

    fun startSheetDismissTimer(function: () -> Unit) {
        sheetDismissTimer?.cancel()
        sheetDismissTimer = Timer().schedule((1000 * 60).toLong()) {
            function()
        }
    }

    fun stopSheetDismissTimer() {
        sheetDismissTimer?.cancel()
    }

    fun resetScreenTimeout(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Timer().schedule(10000) {
            activity.runOnUiThread {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun scanPreviewBuffer(
        view: KikCodeScannerView,
        scanner: KikCodeScanner
    ): Flowable<ScannableKikCode> {
        return Single.defer {
            view.previewSize()
                .subscribeOn(Schedulers.computation())
                .onBackpressureLatest()
                .delay(100, TimeUnit.MILLISECONDS)
                .filter { uiFlow.value.isCameraScanEnabled }
                .filter { it.isPresent }
                .map { it.get()!! }
                .firstOrError()
                .flatMap { previewSize: CameraController.PreviewSize? ->
                    view.getPreviewBuffer()
                        .flatMap { imageData ->
                            scanner.scanKikCode(
                                imageData,
                                previewSize?.width ?: 0,
                                previewSize?.height ?: 0
                            )
                        }
                }
        }.filter {
            uiFlow.value.billState.bill == null
        }.doOnSuccess { code: ScannableKikCode ->
            if (code is ScannableKikCode.RemoteKikCode) {
                onCodeScan(code.payloadId)
            }
        }.delay(3000, TimeUnit.MILLISECONDS)
            .repeat()
            .onErrorResumeNext { error ->
                when (error) {
                    is KikCodeScanner.NoKikCodeFoundException -> Unit // Timber.i("Code Not Found")
                    is LegacyCameraController.NoPreviewException -> Timber.i("No preview")
                    else -> ErrorUtils.handleError(error)
                }
                scanPreviewBuffer(view, scanner)
            }
    }

    fun logout(activity: Activity) {
        authManager.logout(activity)
    }

    @SuppressLint("CheckResult")
    fun onRemoteSend(context: Context) {
        val giftCard = GiftCardAccount.newInstance(context)
        val amount = sendTransactionRepository.getAmount()
        var loadingIndicatorTimer: TimerTask? = null

        if (!networkUtils.isAvailable()) {
            ErrorUtils.showNetworkError(resources)
            return
        }

        client.sendRemotely(
            amount = amount,
            rendezvousKey = sendTransactionRepository.getRendezvous().publicKeyBytes.toPublicKey(),
            giftCard = giftCard
        )
            .doOnSubscribe {
                uiFlow.update { it.copy(isRemoteSendLoading = true) }
            }
            .doOnComplete {
                loadingIndicatorTimer?.cancel()
                loadingIndicatorTimer = Timer().schedule(1000) {
                    uiFlow.update { it.copy(isRemoteSendLoading = false) }
                }
            }
            .doOnError {
                loadingIndicatorTimer?.cancel()
                uiFlow.update { it.copy(isRemoteSendLoading = false) }
            }
            .timeout(15, TimeUnit.SECONDS)
            .subscribe(
                { showRemoteSendDialog(context, giftCard, amount) },
                ErrorUtils::handleError
            )
    }

    private fun cancelRemoteSend(giftCard: GiftCardAccount, amount: KinAmount) {
        val organizer = SessionManager.getOrganizer() ?: return
        client.cancelRemoteSend(giftCard, amount.kin, organizer)
    }

    private fun showRemoteSendDialog(
        context: Context,
        giftCard: GiftCardAccount,
        amount: KinAmount
    ) {
        val url = "https://cash.getcode.com/c/#/e=" +
                giftCard.mnemonicPhrase.getBase58EncodedEntropy(context)
        val text = getString(R.string.subtitle_remoteSendText)
            .replaceParam(
                amount.formatted(
                    currency = currencyUtils.getCurrency(amount.rate.currency.name) ?: Currency.Kin,
                    resources = resources
                )
            )
            .replaceParam(url)

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)

        CoroutineScope(Dispatchers.IO).launch {
            delay(2500)

            BottomBarManager.showMessage(
                BottomBarManager.BottomBarMessage(
                    title = getString(R.string.prompt_title_didYouSendLink),
                    subtitle = getString(R.string.prompt_description_didYouSendLink),
                    positiveText = getString(R.string.action_yes),
                    negativeText = getString(R.string.action_noTryAgain),
                    tertiaryText = getString(R.string.action_cancelSend),
                    onPositive = { cancelSend(style = PresentationStyle.Pop) },
                    onNegative = { showRemoteSendDialog(context, giftCard, amount) },
                    onTertiary = {
                        cancelRemoteSend(giftCard, amount)
                        cancelSend(style = PresentationStyle.Slide)
                    },
                    type = BottomBarManager.BottomBarMessageType.REMOTE_SEND,
                    isDismissible = false,
                    timeoutSeconds = 60
                )
            )
        }
    }

    fun openCashLink(deepLink: String?) {
        val cashLink = deepLink?.trim()?.replace("\n", "") ?: return
        if (cashLink.isEmpty()) return
        if (openedLinks.contains(cashLink)) return

        openedLinks.add(cashLink)

        try {
            val mnemonic =
                MnemonicPhrase.fromEntropyB58(App.getInstance(), cashLink)
            val giftCardAccount =
                GiftCardAccount.newInstance(App.getInstance(), mnemonic)

            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    withTimeout(15000) {
                        balanceController.fetchBalance().blockingAwait()
                        try {
                            //Get the amount on the card
                            val amount = client.receiveRemoteSuspend(giftCardAccount)
                            analyticsManager.cashLinkGrab(amount.kin, amount.rate.currency)
                            analyticsManager.onBillReceived()
                            viewModelScope.launch(Dispatchers.Main) {
                                BottomBarManager.clear()
                                showBill(
                                    Bill.Cash(
                                        amount = amount,
                                        didReceive = true,
                                        kind = Bill.Kind.remote
                                    ),
                                    vibrate = true
                                )
                                removeLinkWithDelay(cashLink)
                                setDeepLinkHandled()
                            }
                        } catch (ex: Exception) {
                            Timber.e(ex)
                            when (ex) {
                                is RemoteSendException -> {
                                    onRemoteSendError(ex)
                                    removeLinkWithDelay(cashLink)
                                    setDeepLinkHandled(withDelay = 0)
                                }

                                else -> {
                                    ErrorUtils.handleError(ex)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Base58.AddressFormatException.InvalidCharacter) {
            ErrorUtils.handleError(e)
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
        }
    }


    private fun onRemoteSendError(throwable: Throwable) {
        when (throwable) {
            is RemoteSendException.GiftCardClaimedException ->
                TopBarManager.showMessage(
                    getString(R.string.error_title_alreadyCollected),
                    getString(R.string.error_description_alreadyCollected)
                )

            is RemoteSendException.GiftCardExpiredException ->
                TopBarManager.showMessage(
                    getString(R.string.error_title_linkExpired),
                    getString(R.string.error_description_linkExpired)
                )

            else -> {
                ErrorUtils.handleError(throwable)
            }
        }
    }

    fun onDrawn() {
        analyticsManager.onAppStarted()
    }

    private fun setDeepLinkHandled(withDelay: Long = 2000) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(withDelay)
            uiFlow.update { it.copy(isDeepLinkHandled = true) }
        }
    }

    companion object {
        private val openedLinks = mutableListOf<String>()
        private val scannedRendezvous = mutableListOf<String>()

        private const val DEBUG_SCAN_TIMES = true
        private var scanProcessingTime = 0L

        fun removeLinkWithDelay(link: String) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(15000)
                openedLinks.remove(link)
            }
        }
    }
}
