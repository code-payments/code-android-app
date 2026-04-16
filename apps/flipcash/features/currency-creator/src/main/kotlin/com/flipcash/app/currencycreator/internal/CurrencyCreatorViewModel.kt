package com.flipcash.app.currencycreator.internal

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.text.trimmedLength
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.mapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBarController
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.currencycreator.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.model.core.errors.CheckTokenAvailabilityError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenCreateRequest
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    resources: ResourceHelper,
    val contentReader: ContentReader,
    val purchaseMethodController: PurchaseMethodController,
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
        val purchaseAmount: Fiat = 20.toFiat(),
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

        data object CheckName: Event
        data object CheckDescription: Event
        data object CheckImage: Event

        data class OnNameApproved(val attestation: ModerationResult.Attestation): Event
        data class OnDescriptionApproved(val attestation: ModerationResult.Attestation): Event
        data class OnImageApproved(val attestation: ModerationResult.Attestation): Event

        data class OnIconSelected(val image: Uri) : Event
        data class OnIconCached(val image: Uri) : Event

        data class OnPurchaseAmountChanged(val amount: Fiat) : Event

        data class OnBillConfirmed(val bill: Bill?): Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object LaunchToken: Event
        data object Purchase: Event
        data class PurchaseWithReserves(val mint: Mint, val amount: Fiat): Event
        data object PurchaseWithGooglePay: Event
    }

    init {
        userFlags.resolvedFlags
            .map { it.newCurrencyPurchaseAmount.effectiveValue }
            .onEach { dispatchEvent(Event.OnPurchaseAmountChanged(it)) }
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
            .map { moderationController.moderateText(it) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> {
                        currencyController.checkTokenAvailability(result.text)
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
                    when (cause) {
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
            .map { moderationController.moderateText(it) }
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
            .filterIsInstance<Event.LaunchToken>()
            .map {
                TokenCreateRequest(
                    name = ModerationAttestation.Text(
                        text = stateFlow.value.nameFieldState.text.toString(),
                        attestation = stateFlow.value.attestations.name.rawValue,
                    ),
                    description = ModerationAttestation.Text(
                        text = stateFlow.value.descriptionFieldState.text.toString(),
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
            }
            .mapNotNull { request ->
                val owner = userManager.accountCluster?.authority?.keyPair ?: return@mapNotNull null
                request to owner
            }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { (request, owner) -> currencyController.launchToken(request, owner) }
            .mapResult { mint ->
                PurchaseMethodMetadata(
                    purchaseAmount = stateFlow.value.purchaseAmount,
                    mint = mint,
                )
            }.onResult(
                onSuccess = { metadata -> purchaseMethodController.present(metadata) },
                onError = {
                    dispatchEvent(Event.UpdateProcessingState())
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_launchTokenFailed),
                        message = resources.getString(R.string.error_description_launchTokenFailed),
                    )
                }
            )
            .launchIn(viewModelScope)

        purchaseMethodController.selections
            .onEach { (method, metadata) ->
                when (method) {
                    PurchaseMethod.CoinbaseOnRamp -> {
                        // TODO:
                    }
                    is PurchaseMethod.CashReserves -> {

                    }
                    PurchaseMethod.PhantomWallet -> {

                    }
                }
            }
            .launchIn(viewModelScope)
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

                is Event.OnBillConfirmed -> { state ->
                    state.copy(bill = event.bill)
                }

                is Event.OnPurchaseAmountChanged -> { state ->
                    state.copy(purchaseAmount = event.amount)
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

                is Event.LaunchToken -> { state -> state }
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
                is Event.PurchaseWithGooglePay -> { state -> state }
            }
        }
    }
}
