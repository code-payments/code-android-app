package com.flipcash.app.currencycreator.internal

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.text.trimmedLength
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.CurrencyCreatorCoordinator
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBarController
import com.flipcash.app.onramp.ExternalWalletOnRampController
import com.flipcash.app.onramp.ExternalWalletOnRampState
import com.flipcash.app.core.tokens.CurrencyCreatorDraft
import com.flipcash.app.tokens.BalancePoller
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.payments.PaymentAction
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.currencycreator.R
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.CheckTokenAvailabilityError
import com.getcode.opencode.model.core.errors.GetMintsError
import com.getcode.opencode.model.core.errors.LaunchTokenError
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenCreateRequest
import com.getcode.opencode.model.financial.fromLaunch
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.orZero
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

internal data class ModerationAttestations(
    val name: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
    val icon: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
    val description: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
)

@HiltViewModel
internal class CurrencyCreatorViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    moderationController: ModerationController,
    currencyController: CurrencyController,
    transactionController: TransactionController,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    externalWalletController: ExternalWalletOnRampController,
    tokenCoordinator: TokenCoordinator,
    balancePoller: BalancePoller,
    resources: ResourceHelper,
    val contentReader: ContentReader,
    val purchaseMethodController: PurchaseMethodController,
    private val currencyCreatorCoordinator: CurrencyCreatorCoordinator,
) : BaseViewModel2<CurrencyCreatorViewModel.State, CurrencyCreatorViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    internal data class State(
        val stepCount: Int = PROGRESS_STEPS.count(),
        val descriptionLengthMax: Int = MAX_DESCRIPTION,
        val currentStep: CurrencyCreatorStep? = null,
        val nameFieldState: TextFieldState = TextFieldState(),
        val descriptionFieldState: TextFieldState = TextFieldState(),
        val icon: Loadable<Uri> = Loadable.Loading(),
        val customizations: TokenBillCustomizations? = null,
        val bill: Bill? = null,
        val createdMint: Mint? = null,
        val launchedToken: Token? = null,
        val purchaseAmount: Fiat = 5.toFiat(),
        val feeAmount: Fiat? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        val attestations: ModerationAttestations = ModerationAttestations(),
    ) {
        val hasName: Boolean
            get() = nameFieldState.text.isNotBlank()

        val descriptionLength: Int
            get() = descriptionFieldState.text.trimmedLength()

        val remainingSpaceForDescription: Int
            get() = descriptionLengthMax - descriptionLength

        val hasDescription: Boolean
            get() = descriptionFieldState.text.isNotBlank() &&
                    remainingSpaceForDescription >= 0

        val progress: Float
            get() {
                val step = currentStep ?: return 0f
                val index = PROGRESS_STEPS.indexOfFirst { it.isInstance(step) }
                if (index < 0) return 0f
                return (index + 1).toFloat() / PROGRESS_STEPS.size
            }

        val totalCost: Fiat
            get() {
                val fee = feeAmount.orZero()
                return purchaseAmount + fee
            }

        private companion object {
            private const val MAX_DESCRIPTION = 500

            val PROGRESS_STEPS = listOf(
                CurrencyCreatorStep.NameSelection::class,
                CurrencyCreatorStep.IconSelection::class,
                CurrencyCreatorStep.DescriptionSelection::class,
                CurrencyCreatorStep.BillCustomization::class,
                CurrencyCreatorStep.BillReviewAndPurchase::class,
            )
        }
    }

    internal sealed interface Event {
        data class OnStepChanged(val step: CurrencyCreatorStep) : Event

        data object CheckName : Event
        data object CheckDescription : Event
        data object CheckImage : Event

        data class OnNameApproved(val attestation: ModerationResult.Attestation) : Event
        data class OnDescriptionApproved(val attestation: ModerationResult.Attestation) : Event
        data class OnImageApproved(val attestation: ModerationResult.Attestation) : Event

        data class OnIconSelected(val image: Uri) : Event
        data class OnIconCached(val image: Uri) : Event
        data object OnIconCleared : Event

        data class OnPurchaseAmountChanged(val amount: Fiat, val feeAmount: Fiat) : Event

        data class OnBillConfirmed(val bill: Bill?) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data class CustomizationsChanged(val customizations: TokenBillCustomizations): Event

        data class LaunchToken(val method: PurchaseMethod) : Event
        data class OnTokenMinted(val mint: Mint): Event
        data object Purchase : Event
        data class PurchaseWithReserves(val context: LaunchedContext) : Event
        data class PurchaseWithPhantom(val context: LaunchedContext) : Event
        data class PurchaseWithGooglePay(val context: LaunchedContext) : Event

        data class PurchaseSubmitted(val swapId: SwapId, val mint: Mint) : Event
        data class PurchaseCompleted(val token: Token): Event
    }

    data class LaunchedContext(
        val method: PurchaseMethod,
        val token: Token,
        val amount: Fiat,
        val feeAmount: Fiat?,
    )

    init {
        userFlags.resolvedFlags
            .onEach { flags ->
                val purchaseAmount = flags.newCurrencyPurchaseAmount.effectiveValue
                val feeAmount = flags.newCurrencyFeeAmount.effectiveValue
                dispatchEvent(Event.OnPurchaseAmountChanged(purchaseAmount, feeAmount))
            }
            .launchIn(viewModelScope)

        // Debounced draft persistence — save state 300ms after changes.
        // The coordinator handles ID assignment; the VM is ID-unaware.
        @OptIn(FlowPreview::class)
        stateFlow
            .drop(1) // skip initial empty state
            .filter { it.currentStep != null }
            .debounce(300)
            .distinctUntilChanged()
            .onEach { state ->
                currencyCreatorCoordinator.saveDraft(
                    CurrencyCreatorDraft(
                        name = state.nameFieldState.text.toString(),
                        description = state.descriptionFieldState.text.toString(),
                        iconUri = state.icon.dataOrNull,
                        customizations = state.customizations,
                        currentStep = state.currentStep!!,
                        nameAttestation = state.attestations.name,
                        iconAttestation = state.attestations.icon,
                        descriptionAttestation = state.attestations.description,
                        createdMint = state.createdMint,
                        savedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)

        // Clear this flow's draft on successful purchase
        eventFlow
            .filterIsInstance<Event.PurchaseCompleted>()
            .onEach { currencyCreatorCoordinator.clearDraft() }
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnIconSelected>()
            .mapNotNull { event ->
                contentReader.copyToCache(
                    uri = event.image,
                    fileName = "currency_icon_${System.nanoTime()}",
                    maxSize = 500
                )
            }
            .flowOn(dispatchers.IO)
            .onEach { cached -> dispatchEvent(Event.OnIconCached(cached)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckName>()
            .map { stateFlow.value.nameFieldState.text.toString() }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { moderationController.moderateText(it.trim()) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> {
                        currencyController.checkTokenAvailability(result.text.trim())
                            .map { result.attestation }
                    }

                    else -> Result.failure(TextModerationError.Flagged(result.flaggedCategory))
                }
            }
            .onResult(
                onSuccess = { attestation ->
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500)
                        dispatchEvent(Event.OnNameApproved(attestation))
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    when (cause) {
                        is ValidationException,
                        is TextModerationError.Flagged,
                        is TextModerationError.Denied -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_nameNotAllowed),
                                message = resources.getString(R.string.error_description_nameNotAllowed)
                            )
                        }

                        is CheckTokenAvailabilityError.Unavailable -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_nameAlreadyTaken),
                                message = resources.getString(R.string.error_description_nameAlreadyTaken)
                            )
                        }

                        else -> {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.error_title_nameCheckFailed),
                                message = resources.getString(R.string.error_description_nameCheckFailed),
                            )
                        }
                    }
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckImage>()
            .mapNotNull { stateFlow.value.icon.dataOrNull }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { moderationController.moderateImage(it) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> Result.success(result.attestation)
                    else -> Result.failure(ImageModerationError.Flagged(result.flaggedCategory))
                }
            }
            .onResult(
                onSuccess = { attestation ->
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500)
                        dispatchEvent(Event.OnImageApproved(attestation))
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    stateFlow.value.icon.dataOrNull?.let { contentReader.removeFromCache(it) }
                    dispatchEvent(Event.OnIconCleared)
                    when (cause) {
                        is ValidationException,
                        is ImageModerationError.Flagged,
                        is ImageModerationError.Denied -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_imageNotAllowed),
                                message = resources.getString(R.string.error_description_nameNotAllowed)
                            )
                        }

                        is ImageModerationError.UnsupportedFormat -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_imageNotSupported),
                                message = resources.getString(R.string.error_description_imageNotSupported)
                            )
                        }

                        else -> {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.error_title_moderationFailed),
                                message = resources.getString(R.string.error_description_moderationFailed),
                            )
                        }
                    }
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckDescription>()
            .map { stateFlow.value.descriptionFieldState.text.toString() }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { moderationController.moderateText(it.trim()) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> Result.success(result.attestation)
                    else -> Result.failure(TextModerationError.Flagged(result.flaggedCategory))
                }
            }
            .onResult(
                onSuccess = { attestation ->
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500)
                        dispatchEvent(Event.OnDescriptionApproved(attestation))
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    when (cause) {
                        is ValidationException,
                        is TextModerationError.Flagged,
                        is TextModerationError.Denied -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_descriptionNotAllowed),
                                message = resources.getString(R.string.error_description_descriptionNotAllowed)
                            )
                        }

                        else -> {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.error_title_moderationFailed),
                                message = resources.getString(R.string.error_description_moderationFailed),
                            )
                        }
                    }
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Purchase>()
            .onEach {
                val metadata = PurchaseMethodMetadata(
                    mint = null,
                    purchaseAmount = stateFlow.value.totalCost,
                    feeAmount = stateFlow.value.feeAmount,
                    paymentAction = PaymentAction.Pay,
                )
                purchaseMethodController.present(metadata)
            }
            .launchIn(viewModelScope)

        purchaseMethodController.selections
            .onEach { (method, _) ->
                dispatchEvent(Event.LaunchToken(method))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LaunchToken>()
            .map { event ->
                println("customizations=${stateFlow.value.customizations}")
                val request = TokenCreateRequest(
                    name = ModerationAttestation.Text(
                        text = stateFlow.value.nameFieldState.text.trim().toString(),
                        attestation = stateFlow.value.attestations.name.rawValue,
                    ),
                    description = ModerationAttestation.Text(
                        text = stateFlow.value.descriptionFieldState.text.trim().toString(),
                        attestation = stateFlow.value.attestations.description.rawValue
                    ),
                    icon = ModerationAttestation.Image(
                        imageBytes = stateFlow.value.icon.dataOrNull?.let {
                            contentReader.readBytes(it)
                        } ?: ByteArray(32),
                        attestation = stateFlow.value.attestations.icon.rawValue,
                    ),
                    bill = stateFlow.value.customizations,
                )
                request to event.method
            }
            .mapNotNull { (request, method) ->
                val accountCluster = userManager.accountCluster ?: return@mapNotNull null
                Triple(request, method, accountCluster)
            }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { (request, method, accountCluster) ->
                currencyController.launchToken(request, accountCluster.authority.keyPair)
                    .recoverCatching { cause ->
                        // The server returns Exists when the mint address is already
                        // registered. If we minted in a prior attempt this session
                        // but the subsequent purchase failed (e.g. transient OCP
                        // error), the mint exists on-chain yet has no funded token
                        // account. Recover the mint so the purchase step can retry.
                        if (cause !is LaunchTokenError.Exists) throw cause
                        val mint = stateFlow.value.createdMint ?: throw cause
                        val notFound = tokenCoordinator.getTokenMetadata(mint)
                            .exceptionOrNull() is GetMintsError.NotFound
                        if (!notFound) throw cause
                        mint
                    }
                    .map { mint ->
                        dispatchEvent(Event.OnTokenMinted(mint))
                        val token = MintMetadata.fromLaunch(
                            mint = mint,
                            request = request,
                            owner = accountCluster.authorityPublicKey,
                        )
                        LaunchedContext(
                            method = method,
                            token = token,
                            amount = stateFlow.value.totalCost,
                            feeAmount = stateFlow.value.feeAmount,
                        )
                    }
            }
            .onResult(
                onSuccess = { ctx ->
                    when (ctx.method) {
                        is PurchaseMethod.CashReserves ->
                            dispatchEvent(Event.PurchaseWithReserves(ctx))

                        PurchaseMethod.PhantomWallet ->
                            dispatchEvent(Event.PurchaseWithPhantom(ctx))

                        PurchaseMethod.CoinbaseOnRamp -> {
                            dispatchEvent(Event.PurchaseWithGooglePay(ctx))
                        }
                    }
                },
                onError = {
                    dispatchEvent(Event.UpdateProcessingState())
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_launchTokenFailed),
                        message = resources.getString(R.string.error_description_launchTokenFailed),
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PurchaseWithPhantom>()
            .onEach { event ->
                // start() must come first — it calls reset() which clears amount/token.
                externalWalletController.start(
                    AppRoute.Token.CurrencyCreator,
                    OnRampProvider.Phantom
                )
                val totalAmount = verifiedFiatCalculator.compute(
                    amount = event.context.amount,
                    token = Token.usdf,
                    rate = Rate.oneToOne,
                )
                val feeAmount = event.context.feeAmount?.let { LocalFiat.fromUsd(usdf = it) }
                externalWalletController.setAmount(amount = totalAmount, feeAmount = feeAmount)
                externalWalletController.setTokenToPurchase(event.context.token)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PurchaseWithReserves>()
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                Pair(owner, event.context)
            }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { (owner, context) ->
                val totalAmount = verifiedFiatCalculator.compute(
                    amount = context.amount,
                    token = Token.usdf,
                    rate = Rate.oneToOne,
                )
                val feeAmount = context.feeAmount?.let { LocalFiat.fromUsd(usdf = it) }
                transactionController.buy(
                    owner = owner,
                    amount = totalAmount,
                    feeAmount = feeAmount,
                    of = context.token,
                    source = SwapFundingSource.SubmitIntent(),
                    fund = null,
                ).map { swapId -> swapId to context.token.address }
            }
            .onResult(
                onSuccess = { (swapId, mint) ->
                    dispatchEvent(Event.PurchaseSubmitted(swapId, mint))
                },
                onError = {
                    dispatchEvent(Event.UpdateProcessingState())
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buyNewCurrencyFailed),
                        message = resources.getString(R.string.error_description_buyNewCurrencyFailed),
                    )
                }
            )
            .launchIn(viewModelScope)

        externalWalletController.state
            .filterIsInstance<ExternalWalletOnRampState.Transacted>()
            .filter { it.origin is AppRoute.Token.CurrencyCreator }
            .mapNotNull { state ->
                val swapId = state.swapId ?: return@mapNotNull null
                val mint = state.token?.address ?: return@mapNotNull null
                swapId to mint
            }
            .onEach { (swapId, mint) ->
                externalWalletController.reset()
                dispatchEvent(Event.PurchaseSubmitted(swapId, mint))
            }
            .launchIn(viewModelScope)

        externalWalletController.state
            .filterIsInstance<ExternalWalletOnRampState.Failed>()
            .onEach {
                dispatchEvent(Event.UpdateProcessingState())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PurchaseSubmitted>()
            .map { event ->
                // currency creation is at *best* 2 mins
                // safe buffer is 5 minutes
                balancePoller.awaitBalanceChange(
                    mint = event.mint,
                    maxAttempts = 300,
                    interval = 1.seconds,
                ).map { event.mint }
            }.flatMapResult { mint ->
                tokenCoordinator.getTokenMetadata(mint)
            }.onResult(
                onSuccess = { result ->
                    dispatchEvent(Event.PurchaseCompleted(result.token))
                    dispatchEvent(Event.UpdateProcessingState(success = true))
                },
                onError = {
                    dispatchEvent(Event.UpdateProcessingState())
                }
            ).launchIn(viewModelScope)
    }

    /**
     * Connect the ViewModel to the top bar controller. The ViewModel pushes
     * [State.progress] to the controller whenever the current step changes.
     */
    fun connectTopBar(controller: CurrencyCreatorTopBarController) {
        stateFlow
            .map { it.currentStep }
            .distinctUntilChanged()
            .onEach { _ ->
                val state = stateFlow.value
                controller.progress = state.progress
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.OnStepChanged -> { state ->
                    state.copy(currentStep = event.step)
                }

                is Event.OnIconSelected -> { state ->
                    state.copy(icon = Loadable.Loading(event.image))
                }

                is Event.OnIconCached -> { state ->
                    state.copy(icon = Loadable.Loaded(event.image))
                }

                is Event.OnIconCleared -> { state ->
                    state.copy(icon = Loadable.Loading())
                }

                is Event.OnBillConfirmed -> { state ->
                    state.copy(bill = event.bill)
                }

                is Event.OnPurchaseAmountChanged -> { state ->
                    state.copy(purchaseAmount = event.amount, feeAmount = event.feeAmount)
                }

                is Event.UpdateProcessingState -> { state ->
                    val processingState = state.processingState

                    state.copy(
                        processingState = processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                is Event.CustomizationsChanged -> { state ->
                    state.copy(customizations = event.customizations)
                }

                is Event.LaunchToken -> { state -> state }

                is Event.OnTokenMinted -> { state -> state.copy(createdMint = event.mint) }

                is Event.Purchase -> { state -> state }
                is Event.CheckName -> { state -> state }
                is Event.CheckDescription -> { state -> state }
                is Event.CheckImage -> { state -> state }
                is Event.OnNameApproved -> { state ->
                    val attestations = state.attestations
                    state.copy(attestations = attestations.copy(name = event.attestation))
                }

                is Event.OnDescriptionApproved -> { state ->
                    val attestations = state.attestations
                    state.copy(attestations = attestations.copy(description = event.attestation))
                }

                is Event.OnImageApproved -> { state ->
                    val attestations = state.attestations
                    state.copy(attestations = attestations.copy(icon = event.attestation))
                }

                is Event.PurchaseWithReserves -> { state -> state }
                is Event.PurchaseWithPhantom -> { state -> state }
                is Event.PurchaseWithGooglePay -> { state -> state }
                is Event.PurchaseSubmitted -> { state -> state }
                is Event.PurchaseCompleted -> { state ->
                    state.copy(launchedToken = event.token)
                }
            }
        }
    }
}
