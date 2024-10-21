package com.getcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.getcode.analytics.AnalyticsManager
import com.getcode.analytics.AnalyticsService
import com.getcode.domain.CashLinkManager
import com.getcode.manager.AuthManager
import com.getcode.manager.GiftCardManager
import com.getcode.manager.SessionManager
import com.getcode.model.BuyModuleFeature
import com.getcode.model.CameraGesturesFeature
import com.getcode.model.Currency
import com.getcode.model.Feature
import com.getcode.model.FlippableTipCardFeature
import com.getcode.model.GalleryFeature
import com.getcode.model.IntentMetadata
import com.getcode.model.InvertedDragZoomFeature
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.PrefsBool
import com.getcode.model.RequestKinFeature
import com.getcode.model.SocialUser
import com.getcode.model.TwitterUser
import com.getcode.model.notifications.NotificationType
import com.getcode.models.Bill
import com.getcode.models.BillState
import com.getcode.models.BillToast
import com.getcode.models.ConfirmationState
import com.getcode.models.DeepLinkRequest
import com.getcode.models.LoginConfirmation
import com.getcode.models.PaymentConfirmation
import com.getcode.models.PaymentValuation
import com.getcode.models.SocialUserPaymentConfirmation
import com.getcode.models.amountFloored
import com.getcode.network.BalanceController
import com.getcode.network.NotificationCollectionHistoryController
import com.getcode.oct24.network.controllers.ChatHistoryController
import com.getcode.network.TipController
import com.getcode.network.client.Client
import com.getcode.network.client.RemoteSendException
import com.getcode.network.client.awaitEstablishRelationship
import com.getcode.network.client.cancelRemoteSend
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.loginToThirdParty
import com.getcode.network.client.receiveFromPrimaryIfWithinLimits
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.client.receiveRemoteSuspend
import com.getcode.network.client.rejectLogin
import com.getcode.network.client.requestFirstKinAirdrop
import com.getcode.network.client.sendRemotely
import com.getcode.network.client.sendRequestToReceiveBill
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AppSettingsRepository
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.FeatureRepository
import com.getcode.network.repository.PaymentRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.ReceiveTransactionRepository
import com.getcode.network.repository.StatusRepository
import com.getcode.util.IntentUtils
import com.getcode.utils.Kin
import com.getcode.extensions.formatted
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.CodePayload
import com.getcode.model.Domain
import com.getcode.model.Kind
import com.getcode.model.Username
import com.getcode.model.toPublicKey
import com.getcode.services.utils.catchSafely
import com.getcode.services.utils.nonce
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.showNetworkError
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.getcode.view.main.scanner.UiElement
import com.kik.kikx.kikcodes.implementation.KikCodeAnalyzer
import com.kik.kikx.models.ScannableKikCode
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.kin.sdk.base.tools.Base58
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface PresentationStyle {
    data object Hidden : PresentationStyle
    sealed interface Visible

    data object Pop : PresentationStyle, Visible
    data object Slide : PresentationStyle, Visible
}

data class SessionState(
    val isCameraPermissionGranted: Boolean? = null,
    val vibrateOnScan: Boolean = false,
    val balance: KinAmount? = null,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val autoStartCamera: Boolean? = null,
    val isCameraScanEnabled: Boolean = true,
    val presentationStyle: PresentationStyle = PresentationStyle.Hidden,
    val billState: BillState = BillState.Default,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val splatTipCard: Boolean = false,
    val notificationUnreadCount: Int = 0,
    val chatUnreadCount: Int = 0,
    val buyModule: Feature = BuyModuleFeature(),
    val requestKin: Feature = RequestKinFeature(),
    val cameraGestures: Feature = CameraGesturesFeature(),
    val invertedDragZoom: Feature = InvertedDragZoomFeature(),
    val gallery: Feature = GalleryFeature(),
    val flippableTipCard: Feature = FlippableTipCardFeature(),
    val scannerElements: List<UiElement> = listOf(
        UiElement.GIVE_KIN,
        UiElement.TIP_CARD,
        UiElement.BALANCE
    ),
    val tipCardConnected: Boolean = false,
)

sealed interface SessionEvent {
    data object PresentTipEntry : SessionEvent
    data object RequestNotificationPermissions : SessionEvent
    data class SendIntent(val intent: Intent) : SessionEvent
    data class OnChatPaidForSuccessfully(val intentId: com.getcode.model.ID, val user: SocialUser): SessionEvent
}

enum class RestrictionType {
    ACCESS_EXPIRED,
    FORCE_UPGRADE,
    TIMELOCK_UNLOCKED
}

@SuppressLint("CheckResult")
@Singleton
class SessionController @Inject constructor(
    private val client: Client,
    private val receiveTransactionRepository: ReceiveTransactionRepository,
    private val paymentRepository: PaymentRepository,
    private val balanceController: BalanceController,
    private val historyController: NotificationCollectionHistoryController,
    private val chatHistoryController: com.getcode.oct24.network.controllers.ChatHistoryController,
    private val tipController: TipController,
    private val prefRepository: PrefRepository,
    private val analytics: AnalyticsService,
    private val authManager: AuthManager,
    private val networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator,
    private val currencyUtils: com.getcode.utils.CurrencyUtils,
    private val exchange: Exchange,
    private val giftCardManager: GiftCardManager,
    private val mnemonicManager: com.getcode.services.manager.MnemonicManager,
    private val cashLinkManager: CashLinkManager,
    private val permissionChecker: PermissionChecker,
    private val notificationManager: NotificationManagerCompat,
    private val codeAnalyzer: KikCodeAnalyzer,
    private val sessionManager: SessionManager,
    appSettings: AppSettingsRepository,
    betaFlagsRepository: BetaFlagsRepository,
    features: FeatureRepository,
) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val state = MutableStateFlow(SessionState())

    private val _eventFlow: MutableSharedFlow<SessionEvent> = MutableSharedFlow()
    val eventFlow: SharedFlow<SessionEvent> = _eventFlow.asSharedFlow()

    private var sheetDismissTimer: TimerTask? = null

    init {
        onDrawn()

        appSettings.observe()
            .map { it.cameraStartByDefault }
            .distinctUntilChanged()
            .onEach { cameraAutoStart ->
                state.update {
                    it.copy(autoStartCamera = cameraAutoStart)
                }
            }.launchIn(scope)

        features.buyModule
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(buyModule = module)
                }
            }.launchIn(scope)

        features.requestKin
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(requestKin = module)
                }
            }.launchIn(scope)

        features.cameraGestures
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(cameraGestures = module)
                }
            }.launchIn(scope)

        features.invertedDragZoom
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(invertedDragZoom = module)
                }
            }.launchIn(scope)

        features.galleryEnabled
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(gallery = module)
                }
            }.launchIn(scope)

        features.tipCardFlippable
            .distinctUntilChanged()
            .onEach { module ->
                state.update {
                    it.copy(flippableTipCard = module)
                }
            }.launchIn(scope)

        betaFlagsRepository.observe()
            .distinctUntilChanged()
            .onEach { betaFlags ->
                state.update { it.copy(scannerElements = buildScannerElements(betaFlags)) }
            }.launchIn(scope)

        tipController.showTwitterSplat
            .onEach { splat ->
                scope.launch {
                    if (splat) {
                        delay(300)
                    } else {
                        delay(500)
                    }
                    state.update {
                        it.copy(splatTipCard = splat)
                    }
                }
            }
            .filter { it }
            .onEach { delay(500) }
            .flatMapLatest { tipController.connectedAccount }
            .filter { tipController.verificationInProgress.value }
            .filterNotNull()
            .distinctUntilChanged()
            .filter { state.value.isCameraScanEnabled }
            .onEach {
                when (it) {
                    is TwitterUser -> {
                        analytics.tipCardLinked()
                        TopBarManager.showMessage(
                            topBarMessage = TopBarManager.TopBarMessage(
                                type = TopBarManager.TopBarMessageType.SUCCESS,
                                title = resources.getString(R.string.success_title_xConnected),
                                message = resources.getString(R.string.success_description_xConnected),
                                primaryText = resources.getString(R.string.action_showMyTipCard),
                                primaryAction = ::presentShareableTipCard,
                                secondaryText = resources.getString(R.string.action_later),
                                secondaryAction = {
                                    tipController.onSeenTipCardBanner()
                                }
                            )
                        )
                    }
                }
            }.launchIn(scope)

        tipController.connectedAccount
            .onEach { account ->
                state.update {
                    it.copy(
                        tipCardConnected = account != null
                    )
                }
            }.launchIn(scope)

        StatusRepository().getIsUpgradeRequired(BuildConfig.VERSION_CODE)
            .subscribeOn(Schedulers.computation())
            .timeout(15_000L, TimeUnit.MILLISECONDS)
            .onErrorComplete { false }
            .subscribe { isUpgradeRequired ->
                state.update { m -> m.copy(restrictionType = if (isUpgradeRequired) RestrictionType.FORCE_UPGRADE else null) }
            }

        SessionManager.authState
            .filter { it.userPrefsUpdated }
            .flatMapLatest {
                prefRepository.observeOrDefault(
                    PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP,
                    false
                )
            }
            .map { it }
            .distinctUntilChanged()
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

                    showToast(amount = amount, isDeposit = true, initialDelay = 1.seconds)

                    historyController.fetch()
                },
                onFailure = {
                    ErrorUtils.handleError(it)
                    prefRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
                }
            )
            .launchIn(scope)

        combine(
            exchange.observeLocalRate(),
            balanceController.observeRawBalance(),
        ) { rate, balance ->
            if (balance == -1.0) {
                null
            } else {
                KinAmount.newInstance(Kin.fromKin(balance), rate)
            }
        }.filterNotNull().onEach { balanceInKin ->
            state.update {
                it.copy(balance = balanceInKin)
            }
        }.launchIn(scope)

        historyController.unreadCount
            .distinctUntilChanged()
            .map { it }
            .onEach { count ->
                state.update { it.copy(notificationUnreadCount = count) }
            }.launchIn(scope)

        chatHistoryController.unreadCount
            .distinctUntilChanged()
            .map { it }
            .onEach { count ->
                state.update { it.copy(chatUnreadCount = count) }
            }.launchIn(scope)

        prefRepository.observeOrDefault(PrefsBool.LOG_SCAN_TIMES, false)
            .flowOn(Dispatchers.IO)
            .onEach { log ->
                withContext(Dispatchers.Main) {
                    state.update {
                        it.copy(logScanTimes = log)
                    }
                }
            }.launchIn(scope)

        prefRepository.observeOrDefault(PrefsBool.VIBRATE_ON_SCAN, false)
            .flowOn(Dispatchers.IO)
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    state.update {
                        it.copy(vibrateOnScan = enabled)
                    }
                }
            }.launchIn(scope)

        prefRepository.observeOrDefault(PrefsBool.SHOW_CONNECTIVITY_STATUS, false)
            .flowOn(Dispatchers.IO)
            .onEach { enabled ->
                withContext(Dispatchers.Main) {
                    state.update {
                        it.copy(showNetworkOffline = enabled)
                    }
                }
            }.launchIn(scope)

        SessionManager.authState
            .distinctUntilChangedBy { it.isTimelockUnlocked }
            .onEach {
                it.let { state ->
                    if (state.isTimelockUnlocked) {
                        this@SessionController.state.update { m -> m.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) }
                    }
                }
            }.launchIn(scope)
    }

    private fun buildScannerElements(
        betaOptions: BetaOptions,
    ): List<UiElement> {
        val actions = mutableListOf(UiElement.GIVE_KIN)
        actions += if (betaOptions.tipCardOnHomeScreen) {
            UiElement.TIP_CARD
        } else {
            UiElement.GET_KIN
        }

        actions += UiElement.BALANCE

        return actions
    }

    fun onCameraScanning(scanning: Boolean) {
        state.update { it.copy(isCameraScanEnabled = scanning) }
    }

    fun onCameraPermissionResult(result: PermissionResult) {
        state.update { it.copy(isCameraPermissionGranted = result == PermissionResult.Granted) }
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
                    state.update {
                        it.copy(
                            billState = it.billState.copy(
                                primaryAction = null,
                                secondaryAction = null,
                            )
                        )
                    }
                } else {
                    state.update {
                        it.copy(
                            billState = it.billState.copy(
                                primaryAction = BillState.Action.Send { onRemoteSend() },
                                secondaryAction = BillState.Action.Cancel(::cancelSend)
                            )
                        )
                    }
                }
            }

            else -> Unit
        }

        cashLinkManager.awaitBillGrab(
            amount = amountFloor,
            organizer = organizer,
            owner = owner,
            onGrabbed = {
                cancelSend(PresentationStyle.Pop)
                vibrator.vibrate()

                scope.launch {
                    client.fetchLimits(true).subscribe({}, ErrorUtils::handleError)
                }
            },
            onTimeout = {
                cancelSend(style = PresentationStyle.Slide)
                analytics.billTimeoutReached(
                    bill.amount.kin,
                    bill.amount.rate.currency,
                    AnalyticsManager.BillPresentationStyle.Slide
                )
            },
            onError = { cancelSend(style = PresentationStyle.Slide) },
            present = { data ->
                if (!bill.didReceive) {
                    trace(
                        tag = "Bill",
                        message = "Pull out cash",
                        metadata = {
                            "amount" to bill.amount
                        },
                        type = TraceType.User,
                    )
                }
                presentSend(data, bill, vibrate)
            }
        )
    }

    private fun presentSend(data: List<Byte>, bill: Bill, isVibrate: Boolean = false) {
        println("present send")
        if (bill.didReceive) {
            state.update {
                val billState = it.billState
                it.copy(
                    billState = billState.copy(
                        valuation = PaymentValuation(
                            bill.amount
                        ),
                    )
                )
            }
        }

        val style: PresentationStyle =
            if (bill.didReceive) PresentationStyle.Pop else PresentationStyle.Slide

        state.update {
            val billState = it.billState
            it.copy(
                presentationStyle = style,
                billState = billState.copy(
                    bill = Bill.Cash(
                        data = data,
                        amount = bill.amount,
                        didReceive = bill.didReceive
                    ),
                    valuation = PaymentValuation(bill.amount),
                    showToast = bill.didReceive
                )
            )
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
        cashLinkManager.cancelSend()
        BottomBarManager.clearByType(BottomBarManager.BottomBarMessageType.REMOTE_SEND)

        scope.launch {
            val shown = showToastIfNeeded(style)
            withContext(Dispatchers.Main) {
                state.update {
                    it.copy(
                        presentationStyle = style,
                    )
                }

                state.update {
                    it.copy(
                        billState = it.billState.copy(
                            bill = null,
                            valuation = null,
                            primaryAction = null,
                            secondaryAction = null,
                        )
                    )
                }
            }

            historyController.fetch()
            balanceController.fetchBalanceSuspend()

            if (shown) {
                delay(5.seconds.inWholeMilliseconds)
            }
            withContext(Dispatchers.Main) {
                state.update {
                    it.copy(
                        billState = it.billState.copy(showToast = false)
                    )
                }
            }
        }
    }

    private fun showToastIfNeeded(
        style: PresentationStyle,
    ): Boolean {
        val billState = state.value.billState
        val bill = billState.bill ?: return false

        if (style is PresentationStyle.Pop || billState.showToast) {
            showToast(
                amount = bill.metadata.kinAmount,
                isDeposit = when (style) {
                    PresentationStyle.Slide -> true
                    PresentationStyle.Pop -> false
                    else -> false
                },
            )

            return true
        }

        return false
    }

    private fun showToast(
        amount: KinAmount,
        isDeposit: Boolean = false,
        initialDelay: Duration = 500.milliseconds
    ) {
        scope.launch {
            delay(initialDelay)
            if (amount.kin.toKinTruncatingLong() == 0L) {
                state.update { uiModel ->
                    val billState = uiModel.billState
                    uiModel.copy(
                        billState = billState.copy(
                            toast = null
                        )
                    )
                }
                return@launch
            }

            state.update {
                it.copy(
                    billState = it.billState.copy(
                        showToast = true,
                        toast = BillToast(amount = amount, isDeposit = isDeposit)
                    )
                )
            }

            delay(5.seconds)

            state.update { uiModel ->
                val billState = uiModel.billState
                uiModel.copy(
                    billState = billState.copy(
                        showToast = false
                    )
                )
            }

            // wait for animation to run
            delay(500.milliseconds)
            state.update { uiModel ->
                val billState = uiModel.billState
                uiModel.copy(
                    billState = billState.copy(
                        toast = null
                    )
                )
            }
        }
    }

    private fun onCodeScan(payload: ByteArray) {
        if (DEBUG_SCAN_TIMES) {
            Timber.tag("codescan").d("start")
            scanProcessingTime = System.currentTimeMillis()
        }

        if (state.value.vibrateOnScan) {
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
            Kind.GiftCard -> {
                trace(
                    tag = "Bill",
                    message = "Scanned cash",
                    type = TraceType.User,
                )
                attemptReceive(organizer, codePayload)
            }

            Kind.RequestPayment,
            Kind.RequestPaymentV2 -> {
                trace(
                    tag = "Bill",
                    message = "Scanned request card",
                    type = TraceType.User,
                )
                attemptPayment(codePayload)
            }

            Kind.Login -> {
                trace(
                    tag = "Bill",
                    message = "Scanned login card",
                    type = TraceType.User,
                )
                attemptLogin(codePayload)
            }

            Kind.Tip -> {
                trace(
                    tag = "Bill",
                    message = "Scanned tip card",
                    type = TraceType.User,
                )
                attemptTip(codePayload)
            }
        }
    }

    private fun attemptPayment(payload: CodePayload, request: DeepLinkRequest? = null) =
        scope.launch {
            val (amount, p) = paymentRepository.attemptRequest(payload) ?: return@launch
            BottomBarManager.clear()

            presentRequest(amount = amount, payload = p, request = request)

            // Ensure that we preemptively pull funds into the
            // correct account before we attempt to pay a request
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }

    private fun attemptTip(codePayload: CodePayload, request: DeepLinkRequest? = null) =
        scope.launch {
            BottomBarManager.clear()
            val username = codePayload.username ?: request?.tipRequest?.username ?: return@launch
            presentTipCard(payload = codePayload, username = username)

            // Ensure that we preemptively pull funds into the
            // correct account before we attempt to tip
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }

    fun presentShareableTipCard() = scope.launch {
        val username = tipController.connectedAccount.value?.username ?: return@launch
        val code = CodePayload(
            kind = Kind.Tip,
            value = Username(username)
        )

        val hasSeenTipCard = tipController.hasSeenTipCard()
        tipController.clearTwitterSplat()

        trace(
            tag = "Bill",
            message = "Show my tip card",
            type = TraceType.User,
        )

        withContext(Dispatchers.Main) {
            state.update {
                val billState = it.billState.copy(
                    bill = Bill.Tip(code, canFlip = state.value.flippableTipCard.enabled),
                    primaryAction = BillState.Action.Share { onRemoteSend() },
                    secondaryAction = BillState.Action.Cancel(::cancelSend)
                )

                it.copy(
                    presentationStyle = PresentationStyle.Slide,
                    billState = billState,
                )
            }
        }

        if (!hasSeenTipCard) {
            showNotificationPermissionHintIfNeeded()
        }
    }

    private suspend fun showNotificationPermissionHintIfNeeded() {
        val isDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionChecker.isDenied(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }

        val channel = notificationManager.getNotificationChannel(NotificationType.ChatMessage.name)
        val isChannelOff = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel?.importance == NotificationManager.IMPORTANCE_NONE
        } else {
            false
        }

        val show = isDenied || isChannelOff

        if (show) {
            delay(400)
            com.getcode.services.manager.ModalManager.showMessage(
                com.getcode.services.manager.ModalManager.Message(
                    icon = R.drawable.ic_bell,
                    title = resources.getString(R.string.title_turnOnNotifications),
                    subtitle = resources.getString(R.string.subtitle_turnOnNotifications),
                    onPositive = {
                        when {
                            isDenied -> {
                                scope.launch {
                                    _eventFlow.emit(SessionEvent.RequestNotificationPermissions)
                                }
                            }

                            else -> {
                                @SuppressLint("NewApi")
                                channel?.importance = NotificationManager.IMPORTANCE_DEFAULT
                            }
                        }

                    },
                    positiveText = resources.getString(R.string.action_allowPushNotifications)
                )
            )
        }
    }

    private suspend fun presentTipCard(
        payload: CodePayload,
        username: String,
    ) {
        vibrator.vibrate()

        withContext(Dispatchers.Main) {
            state.update {
                val billState = it.billState.copy(
                    bill = Bill.Tip(payload),
                    primaryAction = null,
                    secondaryAction = null,
                )

                it.copy(
                    presentationStyle = PresentationStyle.Pop,
                    billState = billState,
                )
            }
        }

        // Tip codes are always the same, we need to
        // ensure that we can scan the same code again
        scannedRendezvous.remove(payload.rendezvous.publicKey)

        runCatching { tipController.fetch(username, payload) }
            .onFailure {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        title = resources.getString(R.string.error_title_tipCardNotActivated),
                        message = resources.getString(R.string.error_description_tipCardNotActivated),
                        primaryText = resources.getString(R.string.action_tweetThem),
                        primaryAction = {
                            val intent = IntentUtils.tweet(
                                resources.getString(
                                    R.string.subtitle_linkingTwitterPrompt, username
                                )
                            )
                            scope.launch {
                                _eventFlow.emit(SessionEvent.SendIntent(intent))
                            }
                            cancelTip()
                        },
                        secondaryText = resources.getString(R.string.action_notNow),
                        secondaryAction = ::cancelTip
                    )
                )
            }.onSuccess {
                delay(300.milliseconds)
                _eventFlow.emit(SessionEvent.PresentTipEntry)
                analytics.tipCardShown(username)
            }
    }

    fun presentTipConfirmation(amount: KinAmount) {
        val scannedUserData = tipController.scannedUserData?.second
        val payload = scannedUserData ?: return
        val metadata = tipController.userMetadata ?: return

        state.update {
            val billState = it.billState.copy(
                socialUserPaymentConfirmation = SocialUserPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    payload = payload,
                    amount = amount,
                    metadata = metadata,
                )
            )

            it.copy(billState = billState)
        }
    }

    fun presentPrivatePaymentConfirmation(socialUser: SocialUser, amount: KinAmount) {
        val payload = CodePayload(
            kind = Kind.Tip,
            value = Username(socialUser.username),
        )


        state.update {
            val billState = it.billState.copy(
                socialUserPaymentConfirmation = SocialUserPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    payload = payload,
                    amount = amount,
                    metadata = socialUser,
                    isPrivate = true,
                    showScrim = true
                )
            )

            it.copy(billState = billState)
        }
    }

    fun completeTipPayment() = scope.launch {
        val tipConfirmation = state.value.billState.socialUserPaymentConfirmation ?: return@launch
        val metadata = tipController.userMetadata ?: return@launch

        val amount = tipConfirmation.amount

        fun showError() {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
            )

            state.update { uiModel ->
                uiModel.copy(
                    presentationStyle = PresentationStyle.Hidden,
                    billState = uiModel.billState.copy(
                        bill = null,
                        showToast = false,
                        socialUserPaymentConfirmation = null,
                        toast = null,
                        valuation = null,
                        primaryAction = null,
                        secondaryAction = null,
                    )
                )
            }
        }

        if (state.value.billState.socialUserPaymentConfirmation == null) {
            showError()
            return@launch
        }

        state.update {
            val billState = it.billState
            it.copy(
                billState = billState.copy(
                    socialUserPaymentConfirmation = tipConfirmation.copy(state = ConfirmationState.Sending)
                ),
            )
        }

        runCatching {
            paymentRepository.completeTipPayment(tipConfirmation.metadata, tipConfirmation.amount)
        }.onSuccess {
            historyController.fetch()
            state.update {
                val billState = it.billState
                val confirmation = it.billState.socialUserPaymentConfirmation ?: return@update it

                it.copy(
                    billState = billState.copy(
                        socialUserPaymentConfirmation = confirmation.copy(state = ConfirmationState.Sent),
                    ),
                )
            }
            delay(400.milliseconds)
            cancelTip()
            showToast(tipConfirmation.amount, isDeposit = false)
        }.onFailure {
            showError()
        }
    }

    fun completePrivatePayment() = scope.launch {
        val confirmation = state.value.billState.socialUserPaymentConfirmation ?: return@launch
        val user = confirmation.metadata
        val amount = confirmation.amount

        state.update {
            val billState = it.billState
            it.copy(
                billState = billState.copy(
                    socialUserPaymentConfirmation = billState.socialUserPaymentConfirmation?.copy(state = ConfirmationState.Sending)
                ),
            )
        }

        runCatching {
            paymentRepository.payForFriendship(user, amount)
        }.onSuccess {
            historyController.fetch()

            state.update { s ->
                val billState = s.billState
                val socialUserPaymentConfirmation = s.billState.socialUserPaymentConfirmation ?: return@update s

                s.copy(
                    billState = billState.copy(
                        socialUserPaymentConfirmation = socialUserPaymentConfirmation.copy(state = ConfirmationState.Sent),
                    ),
                )
            }
            delay(1.seconds)
            cancelTip()
            delay(400.milliseconds)
            showToast(amount, isDeposit = false)
            _eventFlow.emit(SessionEvent.OnChatPaidForSuccessfully(it, user))
        }.onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
            )

            state.update { uiModel ->
                uiModel.copy(
                    presentationStyle = PresentationStyle.Hidden,
                    billState = uiModel.billState.copy(
                        bill = null,
                        showToast = false,
                        socialUserPaymentConfirmation = null,
                        toast = null,
                        valuation = null,
                        primaryAction = null,
                        secondaryAction = null,
                    )
                )
            }
        }
    }

    fun cancelTipEntry() {
        // Cancelling from amount entry is triggered by a UI event.
        // To distinguish between a valid "Next" action that will
        // also dismiss the entry screen, we need to check explicitly
        if (state.value.billState.socialUserPaymentConfirmation == null) {
            cancelTip()
        }
    }

    fun cancelTip() {
        tipController.reset()
        state.update {
            val billState = it.billState.copy(
                bill = null,
                socialUserPaymentConfirmation = null,
                valuation = null,
                primaryAction = null,
                secondaryAction = null,
            )

            it.copy(
                presentationStyle = PresentationStyle.Slide,
                billState = billState
            )
        }
    }

    fun presentRequest(
        amount: KinAmount,
        payload: CodePayload?,
        request: DeepLinkRequest? = null
    ) = scope.launch {
        val code: CodePayload
        if (payload != null) {
            code = payload
        } else {
            val fiat = com.getcode.model.Fiat(currency = amount.rate.currency, amount = amount.fiat)

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
        state.update {
            var billState = it.billState.copy(
                bill = Bill.Payment(amount, code, request),
                valuation = PaymentValuation(amount),
                primaryAction = null,
            )

            if (isReceived) {
                billState = billState.copy(
                    paymentConfirmation = PaymentConfirmation(
                        state = ConfirmationState.AwaitingConfirmation,
                        payload = code,
                        requestedAmount = amount,
                        localAmount = amount.replacing(exchange.localRate)
                    ),
                )
            }

            it.copy(
                presentationStyle = presentationStyle,
                billState = billState,
            )
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

    fun completePayment() = scope.launch {
        // keep bill active while sending
        cashLinkManager.cancelBillTimeout()

        val paymentConfirmation = state.value.billState.paymentConfirmation ?: return@launch
        state.update {
            val billState = it.billState
            it.copy(
                billState = billState.copy(
                    paymentConfirmation = paymentConfirmation.copy(state = ConfirmationState.Sending)
                ),
            )
        }
        runCatching {
            paymentRepository.completePayment(
                paymentConfirmation.requestedAmount,
                paymentConfirmation.payload.rendezvous
            )
        }.onSuccess {
            historyController.fetch()

            state.update {
                val billState = it.billState
                val confirmation = it.billState.paymentConfirmation ?: return@update it

                it.copy(
                    billState = billState.copy(
                        paymentConfirmation = confirmation.copy(state = ConfirmationState.Sent),
                    ),
                )
            }

            delay(400.milliseconds)
            cancelPayment(false)
        }.onFailure { error ->
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
            )

            // Allow the payment request to be scanned again upon a failure state hit
            scannedRendezvous.remove(paymentConfirmation.payload.rendezvous.publicKey)

            ErrorUtils.handleError(error)
            state.update { uiModel ->
                uiModel.copy(
                    presentationStyle = PresentationStyle.Hidden,
                    billState = uiModel.billState.copy(
                        bill = null,
                        showToast = false,
                        paymentConfirmation = null,
                        toast = null,
                        valuation = null,
                        primaryAction = null,
                        secondaryAction = null,
                    )
                )
            }
        }
    }

    private fun cancelPayment(rejected: Boolean, ignoreRedirect: Boolean = false) {
        val paymentRendezous = state.value.billState.paymentConfirmation
        val bill = state.value.billState.bill ?: return
        val amount = bill.amount
        val request = bill.metadata.request

        // only remove the scanned nonce if rejected; completed payments are one time events
        if (rejected) {
            paymentRendezous?.let {
                scannedRendezvous.remove(it.payload.rendezvous.publicKey)
            }
        }

        analytics.requestHidden(amount = amount)

        state.update {
            it.copy(
                presentationStyle = PresentationStyle.Slide,
                billState = it.billState.copy(
                    bill = null,
                    paymentConfirmation = null,
                    valuation = null,
                    primaryAction = null,
                    secondaryAction = null,
                )
            )
        }

        scope.launch {
            delay(300)
            if (rejected) {
                if (!ignoreRedirect) {
                    request?.cancelUrl?.let { url ->
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            url.toUri()
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        _eventFlow.emit(SessionEvent.SendIntent(intent))
                    }
                }
            } else {
                showToast(amount, isDeposit = false)

                if (!ignoreRedirect) {
                    request?.successUrl?.let { url ->
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            url.toUri()
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        _eventFlow.emit(SessionEvent.SendIntent(intent))
                    }
                }
            }
        }
    }

    fun rejectPayment(ignoreRedirect: Boolean = false) {
        val payload = state.value.billState.paymentConfirmation?.payload
        cancelPayment(true, ignoreRedirect)
        payload ?: return

        scope.launch {
            paymentRepository.rejectPayment(payload)
        }
    }

    private fun attemptLogin(codePayload: CodePayload, request: DeepLinkRequest? = null) {
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
        request: DeepLinkRequest? = null
    ) {
        vibrator.vibrate()

        state.update {
            it.copy(
                presentationStyle = PresentationStyle.Pop,
                billState = it.billState.copy(
                    bill = Bill.Login(
                        amount = KinAmount.Zero,
                        payload = payload,
                        request = request,
                    ),
                    loginConfirmation = LoginConfirmation(
                        state = ConfirmationState.AwaitingConfirmation,
                        payload = payload,
                        domain = domain
                    ),
                    primaryAction = null,
                    secondaryAction = null,
                )
            )
        }
    }

    fun completeLogin() = scope.launch {
        val organizer = SessionManager.getOrganizer() ?: return@launch
        val loginConfirmation = state.value.billState.loginConfirmation ?: return@launch
        val domain = loginConfirmation.domain

        state.update {
            val billState = it.billState
            it.copy(
                billState = billState.copy(
                    loginConfirmation = loginConfirmation.copy(state = ConfirmationState.Sending)
                ),
            )
        }

        runCatching {
            val relationship = if (organizer.relationshipFor(domain) == null) {
                client.awaitEstablishRelationship(organizer, domain).getOrNull()
            } else {
                Timber.d("Skipping, relationship already exists.")
                organizer.relationshipFor(domain)
            }

            if (relationship == null) {
                throw IllegalStateException("Relationship not found")
            }

            client.loginToThirdParty(
                rendezvous = loginConfirmation.payload.rendezvous.publicKeyBytes.toPublicKey(),
                relationship = relationship.getCluster().authority.keyPair
            ).getOrThrow()
        }.onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_login_failed),
                resources.getString(R.string.error_description_login_failed),
            )
            ErrorUtils.handleError(it)

            state.update { uiModel ->
                uiModel.copy(
                    presentationStyle = PresentationStyle.Hidden,
                    billState = uiModel.billState.copy(
                        bill = null,
                        showToast = false,
                        loginConfirmation = null,
                        toast = null,
                        valuation = null,
                        primaryAction = null,
                        secondaryAction = null,
                    )
                )
            }
        }.onSuccess {
            state.update {
                val billState = it.billState
                val confirmation = it.billState.loginConfirmation ?: return@update it

                it.copy(
                    billState = billState.copy(
                        loginConfirmation = confirmation.copy(state = ConfirmationState.Sent),
                    ),
                )
            }

            delay(400.milliseconds)
            cancelLogin(rejected = false)
        }
    }

    private fun cancelLogin(rejected: Boolean) {
        val bill = state.value.billState.bill ?: return
        val request = bill.metadata.request
        state.update {
            it.copy(
                presentationStyle = PresentationStyle.Slide,
                billState = it.billState.copy(
                    bill = null,
                    showToast = false,
                    paymentConfirmation = null,
                    loginConfirmation = null,
                    toast = null,
                    valuation = null,
                    primaryAction = null,
                    secondaryAction = null,
                )
            )
        }

        scope.launch {
            delay(300)
            if (rejected) {
                request?.cancelUrl?.let { url ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        url.toUri()
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    _eventFlow.emit(SessionEvent.SendIntent(intent))
                }
            } else {
                request?.successUrl?.let { url ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        url.toUri()
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    _eventFlow.emit(SessionEvent.SendIntent(intent))
                }
            }
        }
    }

    fun rejectLogin() {
        val rendezvous = state.value.billState.loginConfirmation?.payload?.rendezvous
        if (rendezvous == null) {
            Timber.e("Failed to reject login, no rendezous found in login confirmation.")
            return
        }

        cancelLogin(rejected = true)

        scope.launch {
            client.rejectLogin(rendezvous)
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
            .subscribe({
                scope.launch { historyController.fetch() }
            }, {
                scannedRendezvous.remove(payload.rendezvous.publicKey)
                ErrorUtils.handleError(it)
            })
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

    fun onImageSelected(
        uri: Uri
    ) {
        fun onScanningStop() {
            codeAnalyzer.onCodeScanned = {}
            codeAnalyzer.onNoCodeFound = {}
        }

        codeAnalyzer.onCodeScanned = {
            onScanningStop()
            onCodeScan(it)
        }

        codeAnalyzer.onNoCodeFound = {
            onScanningStop()

            TopBarManager.showMessage(
                TopBarManager.TopBarMessage(
                    title = resources.getString(R.string.error_title_noCodeFound),
                    message = resources.getString(R.string.error_description_noCodeFound)
                )
            )
        }

        codeAnalyzer.analyze(uri)
    }

    fun onCodeScan(
        code: ScannableKikCode,
    ) {
        if (state.value.billState.bill == null) {
            if (code is ScannableKikCode.RemoteKikCode) {
                onCodeScan(code.payloadId)
            }
        }
    }

    fun logout(activity: Activity) {
        authManager.logout(activity, onComplete = {})
    }


    @SuppressLint("CheckResult")
    fun onRemoteSend() {
        val bill = state.value.billState.bill
        when (bill) {
            is Bill.Cash -> {
                shareGiftCard()
            }

            is Bill.Tip -> {
                shareTipCard()
            }

            else -> Unit
        }
    }

    private fun shareGiftCard() {
        val giftCard = giftCardManager.createGiftCard()
        val amount = cashLinkManager.amount
        var loadingIndicatorTimer: TimerTask? = null

        if (!networkObserver.isConnected) {
            ErrorUtils.showNetworkError(resources)
            return
        }

        client.sendRemotely(
            amount = amount,
            rendezvousKey = cashLinkManager.rendezvous.publicKeyBytes.toPublicKey(),
            giftCard = giftCard
        )
            .doOnSubscribe {
                state.update { it.copy(isRemoteSendLoading = true) }
            }
            .doOnComplete {
                loadingIndicatorTimer?.cancel()
                loadingIndicatorTimer = Timer().schedule(1000) {
                    state.update { it.copy(isRemoteSendLoading = false) }
                }

                analytics.remoteSendOutgoing(
                    kin = amount.kin,
                    currencyCode = amount.rate.currency
                )
            }
            .doOnError {
                loadingIndicatorTimer?.cancel()
                state.update { it.copy(isRemoteSendLoading = false) }
            }
            .timeout(15, TimeUnit.SECONDS)
            .subscribe(
                { showRemoteSendDialog(giftCard, amount) },
                ErrorUtils::handleError
            )
    }

    private fun shareTipCard() = scope.launch {
        val connectedAccount = tipController.connectedAccount.value ?: return@launch
        withContext(Dispatchers.Main) {
            val shareIntent =
                IntentUtils.tipCard(connectedAccount.username, connectedAccount.platform)

            _eventFlow.emit(SessionEvent.SendIntent(shareIntent))
        }
    }

    private fun cancelRemoteSend(giftCard: GiftCardAccount, amount: KinAmount) =
        scope.launch {
            val organizer = SessionManager.getOrganizer() ?: return@launch
            client.cancelRemoteSend(giftCard, amount.kin, organizer)
                .onSuccess {
                    analytics.remoteSendIncoming(
                        kin = amount.kin,
                        currencyCode = amount.rate.currency,
                        isVoiding = true
                    )
                }
        }

    private fun showRemoteSendDialog(
        giftCard: GiftCardAccount,
        amount: KinAmount
    ) {
        val shareIntent = IntentUtils.cashLink(
            entropy = giftCardManager.getEntropy(giftCard),
            formattedAmount = amount.formatted(
                currency = currencyUtils.getCurrency(amount.rate.currency.name) ?: Currency.Kin,
                resources = resources
            )
        )

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _eventFlow.emit(SessionEvent.SendIntent(shareIntent))
            }
            delay(2500)

            cashLinkManager.cancelBillTimeout()

            BottomBarManager.showMessage(
                BottomBarManager.BottomBarMessage(
                    title = resources.getString(R.string.prompt_title_didYouSendLink),
                    subtitle = resources.getString(R.string.prompt_description_didYouSendLink),
                    positiveText = resources.getString(R.string.action_yes),
                    negativeText = resources.getString(R.string.action_noTryAgain),
                    tertiaryText = resources.getString(R.string.action_cancelSend),
                    onPositive = {
                        cancelSend(style = PresentationStyle.Pop)
                        vibrator.vibrate()
                    },
                    onNegative = { showRemoteSendDialog(giftCard, amount) },
                    onTertiary = {
                        cancelRemoteSend(giftCard, amount)
                        cancelSend(style = PresentationStyle.Slide)
                    },
                    onClose = {
                        if (it == null) {
                            cancelSend(style = PresentationStyle.Pop)
                            vibrator.vibrate()
                        }
                    },
                    type = BottomBarManager.BottomBarMessageType.REMOTE_SEND,
                    isDismissible = false,
                    timeoutSeconds = 60
                )
            )
        }
    }

    fun handleRequest(request: DeepLinkRequest?) {
        if (request != null) {
            when {
                request.paymentRequest != null -> {
                    val payment = request.paymentRequest!!
                    scope.launch {
                        if (state.value.balance == null) {
                            balanceController.fetchBalanceSuspend()
                            state.update {
                                val amount = KinAmount.newInstance(
                                    Kin.fromKin(balanceController.rawBalance), exchange.localRate
                                )
                                it.copy(balance = amount)
                            }
                        }
                        val fiat = payment.fiat
                        val kind =
                            if (payment.fees.isEmpty()) Kind.RequestPayment else Kind.RequestPaymentV2
                        val payload = CodePayload(
                            kind = kind,
                            value = fiat,
                            nonce = request.clientSecret
                        )

                        if (scannedRendezvous.contains(payload.rendezvous.publicKey)) {
                            Timber.d("Nonce previously received: ${payload.nonce.hexEncodedString()}")
                            return@launch
                        }

                        scannedRendezvous.add(payload.rendezvous.publicKey)
                        attemptPayment(payload, request)
                    }
                }

                request.loginRequest != null -> {
                    val payload = CodePayload(
                        kind = Kind.Login,
                        value = Kin.fromKin(0),
                        nonce = request.clientSecret,
                    )

                    if (scannedRendezvous.contains(payload.rendezvous.publicKey)) {
                        Timber.d("Nonce previously received: ${payload.nonce.hexEncodedString()}")
                        return
                    }

                    scannedRendezvous.add(payload.rendezvous.publicKey)
                    attemptLogin(payload, request)
                }

                request.tipRequest != null -> {
                    val tip = request.tipRequest!!
                    val payload = CodePayload(
                        kind = Kind.Tip,
                        value = Username(tip.username)
                    )

                    if (scannedRendezvous.contains(payload.rendezvous.publicKey)) {
                        Timber.d("Nonce previously received: ${payload.nonce.hexEncodedString()}")
                        return
                    }

                    scannedRendezvous.add(payload.rendezvous.publicKey)

                    attemptTip(payload, request)
                }

                request.imageRequest != null -> {
                    val image = request.imageRequest!!
                    onImageSelected(image.uri)
                }
            }
        }
    }

    fun openCashLink(cashLink: String?) {
        Timber.d("openCashLink:$cashLink")
        val base58Entropy = cashLink?.trim()?.replace("\n", "") ?: return
        if (base58Entropy.isEmpty()) {
            Timber.d("cash link empty")
            return
        }
        if (openedLinks.contains(base58Entropy)) {
            Timber.d("cash link already opened in session")
            return
        }

        analytics.cashLinkGrabStart()

        openedLinks.add(base58Entropy)

        try {
            val mnemonic = mnemonicManager.fromEntropyBase58(base58Entropy)
            val giftCardAccount = giftCardManager.createGiftCard(mnemonic)

            scope.launch {
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

                            historyController.fetch()

                            scope.launch(Dispatchers.Main) {
                                BottomBarManager.clear()
                                showBill(
                                    Bill.Cash(
                                        amount = amount,
                                        didReceive = true,
                                        kind = Bill.Kind.remote
                                    ),
                                    vibrate = true
                                )
                                removeLinkWithDelay(base58Entropy)
                            }
                        } catch (ex: Exception) {
                            onRemoteSendError(ex)
                        }
                    }
                }
            }
        } catch (e: Base58.AddressFormatException.InvalidCharacter) {
            onRemoteSendError(e)
        } catch (e: Exception) {
            onRemoteSendError(e)
        }
    }


    private fun onRemoteSendError(throwable: Throwable) {
        when (throwable) {
            is RemoteSendException.GiftCardClaimedException ->
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_alreadyCollected),
                    resources.getString(R.string.error_description_alreadyCollected)
                )

            is RemoteSendException.GiftCardExpiredException ->
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_linkExpired),
                    resources.getString(R.string.error_description_linkExpired)
                )

            else -> {
                throwable.printStackTrace()
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_failedToCollect),
                    resources.getString(R.string.error_description_failedToCollect)
                )
                val traceableError = Throwable(
                    message = "Failed to receive remote send",
                    cause = throwable
                )
                ErrorUtils.handleError(traceableError)
            }
        }
    }

    private fun onDrawn() {
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
