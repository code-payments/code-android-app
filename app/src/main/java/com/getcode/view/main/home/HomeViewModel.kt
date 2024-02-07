package com.getcode.view.main.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.model.ScreenModel
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.analytics.AnalyticsManager
import com.getcode.crypt.MnemonicPhrase
import com.getcode.db.Database
import com.getcode.analytics.AnalyticsService
import com.getcode.manager.AuthManager
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.BetaFlags
import com.getcode.model.CodePayload
import com.getcode.model.Currency
import com.getcode.model.Domain
import com.getcode.model.Fiat
import com.getcode.model.IntentMetadata
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Kind
import com.getcode.model.PrefsBool
import com.getcode.model.Rate
import com.getcode.models.Bill
import com.getcode.models.BillState
import com.getcode.models.BillToast
import com.getcode.models.DeepLinkPaymentRequest
import com.getcode.models.LoginConfirmation
import com.getcode.models.PaymentConfirmation
import com.getcode.models.PaymentState
import com.getcode.models.Valuation
import com.getcode.models.amountFloored
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.client.Client
import com.getcode.network.client.RemoteSendException
import com.getcode.network.client.awaitEstablishRelationship
import com.getcode.network.client.cancelRemoteSend
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.receiveFromPrimaryIfWithinLimits
import com.getcode.network.client.receiveRemoteSuspend
import com.getcode.network.client.requestFirstKinAirdrop
import com.getcode.network.client.sendRemotely
import com.getcode.network.client.sendRequestToReceiveBill
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.PaymentRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.ReceiveTransactionRepository
import com.getcode.network.repository.SendTransactionRepository
import com.getcode.network.repository.StatusRepository
import com.getcode.network.repository.hexEncodedString
import com.getcode.network.repository.replaceParam
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.util.formatted
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.showNetworkError
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.ErrorUtils
import com.getcode.utils.base64EncodedData
import com.getcode.utils.catchSafely
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.nonce
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel
import com.getcode.view.camera.CameraController
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.camera.LegacyCameraController
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
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
    val balance: KinAmount? = null,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val giveRequestsEnabled: Boolean = false,
    val isCameraScanEnabled: Boolean = true,
    val presentationStyle: PresentationStyle = PresentationStyle.Hidden,
    val billState: BillState = BillState.Default,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val chatUnreadCount: Int = 0,
)

sealed interface HomeEvent {
    data class OpenUrl(val url: String) : HomeEvent
}

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
    private val historyController: HistoryController,
    private val prefRepository: PrefRepository,
    private val analytics: AnalyticsService,
    private val authManager: AuthManager,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator,
    private val currencyUtils: CurrencyUtils,
    private val exchange: Exchange,
) : BaseViewModel(resources), ScreenModel {
    val uiFlow = MutableStateFlow(HomeUiModel())

    private val _eventFlow: MutableSharedFlow<HomeEvent> = MutableSharedFlow()
    val eventFlow: SharedFlow<HomeEvent> = _eventFlow.asSharedFlow()

    private var billDismissTimer: TimerTask? = null
    private var sheetDismissTimer: TimerTask? = null
    private var cameraStarted = false
    private var scanPreviewBufferDisposable: Disposable? = null
    private var sendTransactionDisposable: Disposable? = null

    init {
        onDrawn()
        Database.isInit
            .flatMap { prefRepository.getFlowable(PrefsBool.DISPLAY_ERRORS) }
            .subscribe(ErrorUtils::setDisplayErrors)

        StatusRepository().getIsUpgradeRequired(BuildConfig.VERSION_CODE)
            .subscribeOn(Schedulers.computation())
            .subscribe { isUpgradeRequired ->
                if (isUpgradeRequired) {
                    uiFlow.update { m -> m.copy(restrictionType = if (isUpgradeRequired) RestrictionType.FORCE_UPGRADE else null) }
                }
            }

        prefRepository.observeOrDefault(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
            .map { it }
            .distinctUntilChanged()
            .onEach { Timber.d("airdrop eligible=$it") }
            .filter { it }
            .mapNotNull { SessionManager.getKeyPair() }
            .catchSafely(
                action = { owner ->
                    val amount = client.requestFirstKinAirdrop(owner).getOrThrow()
                    prefRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
                    balanceController.fetchBalanceSuspend()

                    val organizer = SessionManager.getOrganizer()
                    val receiveWithinLimits = organizer?.let {
                        client.receiveFromPrimaryIfWithinLimits(it)
                    } ?: Completable.complete()
                    receiveWithinLimits.subscribe({}, {})

                    showToast(amount = amount, isDeposit = true)

                    historyController.fetchChats()
                },
                onFailure = {
                    Timber.e(t = it, message = "Auto airdrop failed")
                }
            )
            .launchIn(viewModelScope)

        combine(
            exchange.observeLocalRate(),
            balanceController.observeRawBalance(),
        ) { rate, balance ->
            KinAmount.newInstance(Kin.fromKin(balance), rate)
        }.onEach { balanceInKin ->
            uiFlow.update {
                it.copy(balance = balanceInKin)
            }
        }.launchIn(viewModelScope)

        historyController.unreadCount
            .distinctUntilChanged()
            .map { it }
            .onEach { count ->
                uiFlow.update { it.copy(chatUnreadCount = count) }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.LOG_SCAN_TIMES, false)
            .flowOn(Dispatchers.IO)
            .onEach { log ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(logScanTimes = log)
                    }
                }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.GIVE_REQUESTS_ENABLED, false)
            .flowOn(Dispatchers.IO)
            .filter { BetaFlags.isAvailable(PrefsBool.GIVE_REQUESTS_ENABLED) }
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(giveRequestsEnabled = enabled)
                    }
                }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.VIBRATE_ON_SCAN, false)
            .flowOn(Dispatchers.IO)
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    uiFlow.update {
                        it.copy(vibrateOnScan = enabled)
                    }
                }
            }.launchIn(viewModelScope)

        prefRepository.observeOrDefault(PrefsBool.SHOW_CONNECTIVITY_STATUS, false)
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
                .distinctUntilChangedBy { it.isTimelockUnlocked }
                .collectLatest {
                    it.let { state ->
                        if (state.isTimelockUnlocked) {
                            uiFlow.update { m -> m.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) }
                        }
                    }
                }
        }
    }

    fun reset() {
        uiFlow.update { HomeUiModel() }
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

        if (!networkObserver.isConnected) {
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
                        client.fetchLimits(isForce = true),
                    )
                }
                .subscribe({
                    cancelSend(PresentationStyle.Pop)
                    vibrator.vibrate()
                    viewModelScope.launch { historyController.fetchChats() }
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
                analytics.billTimeoutReached(
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
                            valuation = Valuation(bill.amount),
                            showToast = bill.didReceive
                        )
                    )
                }
            }

            if (style is PresentationStyle.Visible) {
                analytics.billShown(
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

    fun cancelSend(style: PresentationStyle = PresentationStyle.Slide) {
        billDismissTimer?.cancel()
        sendTransactionDisposable?.dispose()
        BottomBarManager.clearByType(BottomBarManager.BottomBarMessageType.REMOTE_SEND)

        val shown = showToastIfNeeded(style)

        uiFlow.update {
            it.copy(
                presentationStyle = style,
                billState = it.billState.copy(
                    bill = null,
                )
            )
        }

        viewModelScope.launch {
            if (shown) {
                delay(300)
            }
            withContext(Dispatchers.Main) {
                uiFlow.update {
                    it.copy(
                        billState = it.billState.copy(
                            valuation = null,
                            hideBillButtons = false
                        )
                    )
                }
            }
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
                uiFlow.update { uiModel ->
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

        if (!networkObserver.isConnected) {
            scannedRendezvous.remove(codePayload.rendezvous.publicKey)
            return ErrorUtils.showNetworkError(resources)
        }

        when (codePayload.kind) {
            Kind.Cash,
            Kind.GiftCard -> attemptReceive(organizer, codePayload)

            Kind.RequestPayment -> attemptPayment(codePayload)

            Kind.Login -> {
                //attemptLogin(codePayload)
            }
        }
    }

    private fun attemptPayment(payload: CodePayload, request: DeepLinkPaymentRequest? = null) =
        viewModelScope.launch {
            val (amount, p) = paymentRepository.attemptRequest(payload) ?: return@launch
            BottomBarManager.clear()

            presentRequest(amount = amount, payload = p, request = request)
        }

    fun presentRequest(
        amount: KinAmount,
        payload: CodePayload?,
        request: DeepLinkPaymentRequest? = null
    ) = viewModelScope.launch {
        val code: CodePayload
        if (payload != null) {
            code = payload
        } else {
            val fiat = Fiat(currency = amount.rate.currency, amount = amount.fiat)
            code = CodePayload(
                kind = Kind.RequestPayment,
                value = fiat,
                nonce = nonce
            )

            val organizer = SessionManager.getOrganizer() ?: return@launch
            client.sendRequestToReceiveBill(
                destination = organizer.primaryVault,
                fiat = fiat,
                rendezvous = code.rendezvous
            )
        }

        val isReceived = payload != null
        val presentationStyle = if (isReceived) PresentationStyle.Pop else PresentationStyle.Slide
        withContext(Dispatchers.Main) {
            uiFlow.update {
                var billState = it.billState.copy(
                    bill = Bill.Payment(amount, code, request),
                )

                if (isReceived) {
                    billState = billState.copy(
                        paymentConfirmation = PaymentConfirmation(
                            state = PaymentState.AwaitingConfirmation,
                            payload = code,
                            requestedAmount = amount,
                            localAmount = amount.replacing(exchange.localRate)
                        ),
                        hideBillButtons = true
                    )
                }

                it.copy(
                    presentationStyle = presentationStyle,
                    billState = billState,
                )
            }
        }

        analytics.requestShown(amount = amount)

        if (DEBUG_SCAN_TIMES) {
            if (scanProcessingTime > 0) {
                Timber.tag("codescan")
                    .d("scan processing took ${System.currentTimeMillis() - scanProcessingTime}")
                scanProcessingTime = 0
            }
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
        }.onSuccess {
            showToast(paymentConfirmation.localAmount, false)

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

            delay(1.seconds)
            cancelPayment(false)
        }.onFailure { error ->
            error.printStackTrace()
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
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

    private fun cancelPayment(rejected: Boolean, ignoreRedirect: Boolean = false) {
        val paymentRendezous = uiFlow.value.billState.paymentConfirmation
        val bill = uiFlow.value.billState.bill ?: return
        val amount = bill.amount
        val request = bill.metadata.request

        paymentRendezous?.let {
            scannedRendezvous.remove(it.payload.rendezvous.publicKey)
        }

        analytics.requestHidden(amount = amount)

        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Slide,
                billState = it.billState.copy(
                    bill = null,
                    paymentConfirmation = null,
                    valuation = null,
                    hideBillButtons = false,
                )
            )
        }

        viewModelScope.launch {
            delay(300)
            if (rejected) {
                if (!ignoreRedirect) {
                    request?.cancelUrl?.let {
                        _eventFlow.emit(HomeEvent.OpenUrl(it))
                    }
                }
            } else {
                showToast(amount, isDeposit = false)

                if (!ignoreRedirect) {
                    request?.successUrl?.let {
                        _eventFlow.emit(HomeEvent.OpenUrl(it))
                    }
                }
            }
        }
    }

    fun rejectPayment(ignoreRedirect: Boolean = false) {
        val payload = uiFlow.value.billState.paymentConfirmation?.payload
        cancelPayment(true, ignoreRedirect)
        payload ?: return

        viewModelScope.launch {
            paymentRepository.rejectPayment(payload)
        }
    }

    private fun attemptLogin(codePayload: CodePayload, request: DeepLinkPaymentRequest? = null) {
        val (payload, loginAttempt) = paymentRepository.attemptLogin(codePayload) ?: return
        BottomBarManager.clear()

        presentLoginCard(
            payload = payload,
            domain = loginAttempt.domain,
            request = request,
        )
    }

    private fun presentLoginCard(
        payload: CodePayload,
        domain: Domain,
        request: DeepLinkPaymentRequest? = null
    ) {
        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Pop,
                billState = it.billState.copy(
                    bill = Bill.Login(
                        amount = KinAmount.newInstance(Kin.fromKin(0), Rate.oneToOne),
                        payload = payload,
                        request = request,
                    ),
                    loginConfirmation = LoginConfirmation(
                        payload = payload,
                        domain = domain
                    ),
                    hideBillButtons = true
                )
            )
        }
    }

    fun completeLogin(domain: Domain) = viewModelScope.launch {
        val organizer = SessionManager.getOrganizer() ?: return@launch
        if (organizer.relationshipFor(domain) == null) {
            client.awaitEstablishRelationship(organizer, domain)
        } else {
            Timber.d("Skipping, relationship already exists.")
        }
    }

    fun cancelLogin() {
        uiFlow.update {
            it.copy(
                presentationStyle = PresentationStyle.Hidden,
                billState = it.billState.copy(
                    bill = null,
                    showToast = false,
                    paymentConfirmation = null,
                    loginConfirmation = null,
                    toast = null,
                    valuation = null,
                    hideBillButtons = false,
                )
            )
        }
    }

    fun rejectLogin() {
        val rendezvous = uiFlow.value.billState.loginConfirmation?.payload?.rendezvous
        if (rendezvous == null) {
            Timber.e("Failed to reject login, no rendezous found in login confirmation.")
            return
        }

        cancelLogin()
        viewModelScope.launch {
            paymentRepository.rejectLogin(rendezvous)
        }
    }

    @SuppressLint("CheckResult")
    private fun attemptReceive(organizer: Organizer, payload: CodePayload) {
        analytics.grabStart()
        receiveTransactionRepository.start(organizer, payload.rendezvous)
            .doOnNext { metadata ->
                Timber.d("metadata=$metadata")
                val kinAmount = when (metadata) {
                    is IntentMetadata.SendPrivatePayment -> metadata.metadata.amount
                    is IntentMetadata.ReceivePaymentsPublicly -> metadata.metadata.amount
                    else -> return@doOnNext
                }

                analytics.grab(kin = kinAmount.kin, currencyCode = kinAmount.rate.currency)

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
                    Timber.tag("codescan")
                        .d("scan processing took ${System.currentTimeMillis() - scanProcessingTime}")
                    scanProcessingTime = 0
                }
            }
            .flatMapCompletable {
                Completable.concatArray(
                    balanceController.fetchBalance(),
                    client.fetchLimits(isForce = true),
                )
            }
            .subscribe({ viewModelScope.launch { historyController.fetchChats() } }, {
                scannedRendezvous.remove(payload.rendezvous.publicKey)
                ErrorUtils.handleError(it)
            })
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
        if (!view.previewing) {
            view.startPreview()
        }
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
        authManager.logout(activity, onComplete = {})
    }

    @SuppressLint("CheckResult")
    fun onRemoteSend(context: Context) {
        val giftCard = GiftCardAccount.newInstance(context)
        val amount = sendTransactionRepository.getAmount()
        var loadingIndicatorTimer: TimerTask? = null

        if (!networkObserver.isConnected) {
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

                analytics.remoteSendOutgoing(
                    kin = amount.kin,
                    currencyCode = amount.rate.currency
                )
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

    private fun cancelRemoteSend(giftCard: GiftCardAccount, amount: KinAmount) =
        viewModelScope.launch {
            val organizer = SessionManager.getOrganizer() ?: return@launch
            client.cancelRemoteSend(giftCard, amount.kin, organizer)
            analytics.remoteSendIncoming(
                kin = amount.kin,
                currencyCode = amount.rate.currency,
                isVoiding = true
            )
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
                    onPositive = {
                        cancelSend(style = PresentationStyle.Pop)
                        vibrator.vibrate()
                    },
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

    fun handlePaymentRequest(bytes: String) {
        val data = bytes.base64EncodedData()
        val request = DeepLinkPaymentRequest.from(data)
        if (request != null) {
            val payload = CodePayload(
                kind = Kind.RequestPayment,
                value = request.fiat,
                nonce = request.clientSecret
            )

            if (scannedRendezvous.contains(payload.rendezvous.publicKey)) {
                Timber.d("Nonce previously received: ${payload.nonce.hexEncodedString()}")
                return
            }

            scannedRendezvous.add(payload.rendezvous.publicKey)
            attemptPayment(payload, request)
        }
    }

    fun openCashLink(deepLink: String?) {
        Timber.d("openCashLink: deep link=$deepLink")
        val cashLink = deepLink?.trim()?.replace("\n", "") ?: return
        if (cashLink.isEmpty()) {
            Timber.d("cash link empty")
            return
        }
        if (openedLinks.contains(cashLink)) {
            Timber.d("cash link already opened in session")
            return
        }

        analytics.cashLinkGrabStart()

        openedLinks.add(cashLink)

        try {
            val mnemonic =
                MnemonicPhrase.fromEntropyB58(App.getInstance(), cashLink)
            val giftCardAccount =
                GiftCardAccount.newInstance(App.getInstance(), mnemonic)

            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    withTimeout(15000) {
                        balanceController.fetchBalanceSuspend()
                        try {
                            //Get the amount on the card
                            val amount = client.receiveRemoteSuspend(giftCardAccount)
                            analytics.remoteSendIncoming(
                                kin = amount.kin,
                                currencyCode = amount.rate.currency,
                                isVoiding = false
                            )
                            analytics.cashLinkGrab(amount.kin, amount.rate.currency)
                            analytics.onBillReceived()
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
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            Timber.e(ex)
                            when (ex) {
                                is RemoteSendException -> {
                                    onRemoteSendError(ex)
                                    removeLinkWithDelay(cashLink)
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
        analytics.onAppStarted()
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
