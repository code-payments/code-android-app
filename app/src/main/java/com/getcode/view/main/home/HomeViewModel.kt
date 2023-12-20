package com.getcode.view.main.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.viewModelScope
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
import com.getcode.network.BalanceController
import com.getcode.network.client.*
import com.getcode.network.repository.*
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.utils.ErrorUtils
import com.getcode.util.FormatAmountUtils
import com.getcode.util.VibrationUtil
import com.getcode.util.showNetworkError
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
import kotlin.concurrent.timerTask

data class HomeUiModel(
    val isCameraPermissionGranted: Boolean? = null,
    val isCameraScanEnabled: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    val selectedBottomSheet: HomeBottomSheet? = null,
    val isBillVisible: Boolean = false,
    val isBillSlideInAnimated: Boolean = true,
    val isBillSlideOutAnimated: Boolean = true,
    val billAmount: KinAmount? = null,
    val billPayloadData: List<Byte>? = null,
    val billReceivedAmountText: String? = null,
    val isReceiveDialogVisible: Boolean = false,
    val isBalanceChangeToastVisible: Boolean = false,
    val balanceChangeToastText: String? = null,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val isDeepLinkHandled: Boolean = false,
)

enum class RestrictionType {
    ACCESS_EXPIRED,
    FORCE_UPGRADE,
    TIMELOCK_UNLOCKED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val client: Client,
    private val sendTransactionRepository: SendTransactionRepository,
    private val receiveTransactionRepository: ReceiveTransactionRepository,
    private val balanceController: BalanceController,
    private val prefRepository: PrefRepository,
    private val analyticsManager: AnalyticsManager,
    private val authManager: AuthManager,
) : BaseViewModel() {
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

    fun onHideBottomSheet() {
        uiFlow.update {
            it.copy(
                isBottomSheetVisible = false,
                isCameraScanEnabled = true,
                selectedBottomSheet = null
            )
        }
    }

    fun onShowBottomSheet() {
        uiFlow.value = uiFlow.value.copy(isBottomSheetVisible = true)
    }

    fun onCameraPermissionChanged(isGranted: Boolean) {
        uiFlow.update { it.copy(isCameraPermissionGranted = isGranted) }
    }

    fun showBill(
        amount: KinAmount,
        isReceived: Boolean = false,
        isVibrate: Boolean = false
    ) {
        val amountFloor = amount.copy(kin = amount.kin.toKinTruncating())

        if (amountFloor.fiat == 0.0 || amount.kin.toKinTruncatingLong() == 0L) return
        val owner = SessionManager.getKeyPair() ?: return

        if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
            return ErrorUtils.showNetworkError()
        }

        analyticsManager.billShown(
            amountFloor.kin,
            amountFloor.rate.currency,
            if (isReceived) {
                AnalyticsManager.BillPresentationStyle.Pop
            } else {
                AnalyticsManager.BillPresentationStyle.Slide
            }
        )

        val organizer = SessionManager.getOrganizer() ?: return

        // this should not be in the view model
        sendTransactionDisposable?.dispose()
        sendTransactionRepository.init(amount = amountFloor, owner = owner)
        sendTransactionDisposable = sendTransactionRepository.startTransaction(App.getInstance(), organizer)
            .doOnNext {
                hideBill(isSent = true, isVibrate = true)
                analyticsManager.billHidden(
                    amountFloor.kin,
                    amountFloor.rate.currency,
                    AnalyticsManager.BillPresentationStyle.Pop
                )
            }
            .flatMapCompletable {
                Completable.concatArray(
                    balanceController.fetchBalance(),
                    client.fetchLimits(isForce = true)
                )
            }
            .subscribe({
            }, {
                ErrorUtils.handleError(it)
                hideBill(isSent = false, isVibrate = false)
            })

        updateBillState(amountFloor, sendTransactionRepository.payloadData, isReceived, isVibrate)
    }

    private fun updateBillState(
        amount: KinAmount,
        billPayloadData: List<Byte>,
        isReceived: Boolean = false,
        isVibrate: Boolean = false
    ) {
        billDismissTimer?.cancel()
        billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
            hideBill(isVibrate = false)
            analyticsManager.billTimeoutReached(
                amount.kin,
                amount.rate.currency,
                AnalyticsManager.BillPresentationStyle.Slide
            )
        }

        uiFlow.update {
            it.copy(
                isBillVisible = true,
                isReceiveDialogVisible = isReceived,
                billPayloadData = billPayloadData,
                billAmount = amount,
                billReceivedAmountText = FormatAmountUtils.formatAmountString(amount),
                isBillSlideInAnimated = !isReceived,
                isBillSlideOutAnimated = true,
            )
        }
        if (isVibrate) {
            Timer().schedule(timerTask {
                VibrationUtil.vibrate()
            }, 150)
        }
    }

    fun hideBill(isSent: Boolean? = null, isVibrate: Boolean = false) {
        billDismissTimer?.cancel()
        sendTransactionDisposable?.dispose()
        BottomBarManager.clearByType(BottomBarManager.BottomBarMessageType.REMOTE_SEND)

        if (!uiFlow.value.isBillVisible && !uiFlow.value.isReceiveDialogVisible) {
            return
        }

        uiFlow.update { uiModel ->
            val amount = uiModel.billAmount
            if (amount != null && !uiModel.isBalanceChangeToastVisible &&
                (uiModel.isReceiveDialogVisible || isSent == true)) {
                showBalanceChangeToast(amount, isNegative = isSent == true)
            }

            uiModel.copy(
                isBillVisible = false,
                isReceiveDialogVisible = false
            ).let {
                if (isSent != null) {
                    it.copy(isBillSlideOutAnimated = !isSent)
                } else {
                    it
                }
            }
        }
        if (isVibrate) {
            VibrationUtil.vibrate()
        }
    }

    private fun showBalanceChangeToast(balanceChangeAmount: KinAmount, isNegative: Boolean = false) {
        val amount = balanceChangeAmount.kin.toKinTruncatingLong()
        if (amount == 0L) {
            uiFlow.update {
                it.copy(isBalanceChangeToastVisible = false)
            }
            return
        }

        val amountText = StringBuilder()
            .append(if (isNegative) "-" else "+")
            .append(FormatAmountUtils.formatAmountString(balanceChangeAmount))
            .toString()

        uiFlow.update {
            it.copy(isBalanceChangeToastVisible = true, balanceChangeToastText = amountText)
        }
        Timer().schedule(5000) {
            uiFlow.update {
                it.copy(isBalanceChangeToastVisible = false)
            }
        }
    }

    private fun onCodeScan(payload: ByteArray) {
        if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
            return ErrorUtils.showNetworkError()
        }

        runBlocking {
            balanceController.fetchBalanceSuspend()
            client.receiveIfNeeded().blockingAwait()
        }

        prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, false)
            .subscribe { value: Boolean ->
                if (value) VibrationUtil.vibrate()
            }

        analyticsManager.grabStart()
        val organizer = SessionManager.getOrganizer() ?: return

        receiveTransactionRepository.start(organizer, payload.toList())
            .doOnNext { metadata ->
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
                    amount = kinAmount,
                    isReceived = true,
                    isVibrate = true,
                )
            }
            .flatMapCompletable {
                Completable.concatArray(
                    balanceController.fetchBalance(),
                    client.fetchLimits(isForce = true)
                )
            }
            .subscribe({ }, ErrorUtils::handleError)
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
        }
            .doOnSuccess { code: ScannableKikCode ->
                if (code is ScannableKikCode.RemoteKikCode) {
                    onCodeScan(code.payloadId)
                }
            }
            .delay(3000, TimeUnit.MILLISECONDS)
            .repeat()
            .onErrorResumeNext { error ->
                when (error) {
                    is KikCodeScanner.NoKikCodeFoundException -> Unit //Timber.i("Code Not Found")
                    is LegacyCameraController.NoPreviewException -> Timber.i("No preview")
                    else -> ErrorUtils.handleError(error)
                }
                scanPreviewBuffer(view, scanner)
            }
    }

    fun logout(activity: Activity) {
        authManager.logout(activity)
    }

    fun onRemoteSend(context: Context) {
        val giftCard = GiftCardAccount.newInstance(context)
        val amount = sendTransactionRepository.getAmount()
        var loadingIndicatorTimer: TimerTask? = null

        if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
            ErrorUtils.showNetworkError()
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
            .replaceParam(FormatAmountUtils.formatAmountString(amount))
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
                    onPositive = { },
                    onNegative = { showRemoteSendDialog(context, giftCard, amount) },
                    onTertiary = { cancelRemoteSend(giftCard, amount) },
                    onClose = {
                        val isPositive = it == BottomBarManager.BottomBarActionType.Positive
                        val isTertiary = it == BottomBarManager.BottomBarActionType.Tertiary
                        if (isPositive || isTertiary) {
                            hideBill(isSent = isPositive, isVibrate = isPositive)
                        }
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
                                showBill(amount, isReceived = true, isVibrate = true)
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
                                } else -> {
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

        fun removeLinkWithDelay(link: String) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(15000)
                openedLinks.remove(link)
            }
        }
    }
}