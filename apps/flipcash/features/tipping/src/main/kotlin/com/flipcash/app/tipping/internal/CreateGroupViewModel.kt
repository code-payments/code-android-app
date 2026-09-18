package com.flipcash.app.tipping.internal

import android.net.Uri
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ImageUploadPreparer
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.BlobRejectedException
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.StartChatError
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.GroupAccess
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * The three preset balance requirements on the form — node 10127:118014's `$10 / $50 / $100` chips.
 *
 * USD, because that is the currency a token balance is held in
 * ([TokenWithBalance.balance]) and therefore the one [GroupAccess] compares a requirement against.
 * The fourth chip is the `…`, which opens [NewGroupStep.CustomAmount] instead of carrying a value.
 */
internal val BalancePresets: List<Fiat> = listOf(Fiat(10), Fiat(50), Fiat(100))

/**
 * The draft behind creating a public group — section 10153:22901 — shared across the flow's steps.
 *
 * One view model for all four steps rather than one each, because they are four views of a single
 * draft: the title and picture are entered on the form, the mint on the currency sheet, the amount
 * on a chip or the custom keypad, and the invite step shares what the draft became. Holding it here
 * is also what gives an attempt's idempotency key a lifetime longer than a single screen — see
 * [GroupCreateAttempt].
 *
 * Two rules are enforced before the call rather than after it. The creator has to satisfy the rules
 * they are setting, so Create stays inert while the selected mint's balance is under the amount —
 * `StartChat` would answer `RULES_NOT_SATISFIED`, which is a worse way to learn it. And the picture
 * has to be uploaded and `READY` first, because `GroupChatParameters.picture` is a blob id, not
 * bytes. Both server results are still handled: a balance can move between the check and the call.
 */
@HiltViewModel
internal class CreateGroupViewModel @Inject constructor(
    exchange: Exchange,
    tokenCoordinator: TokenCoordinator,
    dispatchers: DispatcherProvider,
    private val chatCoordinator: ChatCoordinator,
    private val blobStorage: BlobStorageCoordinator,
    private val imagePreparer: ImageUploadPreparer,
    private val contentReader: ContentReader,
    private val resources: ResourceHelper,
) : BaseViewModel<CreateGroupViewModel.State, CreateGroupViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val titleFieldState: TextFieldState = TextFieldState(),
        /** The picked picture's re-encoded local copy, while it is being prepared and after. */
        val image: Loadable<Uri> = Loadable.Loading(),
        val imageMimeType: String = "",
        val uploadPolicy: UploadPolicy? = null,
        /** What the account holds, which is both the currency list and the self-satisfaction check. */
        val balances: List<TokenWithBalance> = emptyList(),
        /** The preferred rate, for reading a custom keypad entry back into USD. */
        val rate: Rate = Rate.oneToOne,
        val mint: Mint? = null,
        /**
         * Whether the creator has picked the mint, rather than inherited the one the form opened
         * on. Not `mint != null`: the form always opens with one named, so nothing about the mint
         * itself distinguishes a default from a choice.
         */
        val mintChosen: Boolean = false,
        val amount: Fiat? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        /** The chat `StartChat` returned. Non-null is the whole precondition for the invite step. */
        val created: ChatMetadata? = null,
    ) {
        val title: String
            get() = titleFieldState.text.toString().trim()

        val hasTitle: Boolean
            get() = title.isNotEmpty()

        /** The selected mint's metadata, which is where the row's name and image come from. */
        val token: Token?
            get() = mint?.let { selected -> balances.firstOrNull { it.token.address == selected }?.token }

        /**
         * What the form and its errors call the requirement's currency — `$BadBoys` in node
         * 10127:118194, `Jeffy` in 10127:118014.
         *
         * [Token.name], not [Token.symbol]: the name is what the design shows and what the balance
         * list this mint was picked from already renders
         * ([com.getcode.opencode.model.financial.TokenWithBalance.displayName]), so the symbol
         * would name the same holding two different ways one screen apart.
         */
        val currencyName: String?
            get() = token?.name

        /**
         * The rules the draft describes, once it describes any.
         *
         * Listener only, and exactly one mint. A listener requirement is what gates *entry*, which
         * is what this design sets; `speaker` is left empty rather than null because
         * [ChatRules] holds both as lists.
         */
        val rules: ChatRules?
            get() {
                val mint = mint ?: return null
                val amount = amount ?: return null
                return ChatRules(
                    listener = listOf(ChatRuleRequirement.MinimumBalance(amount, listOf(mint))),
                    speaker = emptyList(),
                )
            }

        /**
         * The creator measured against their own rule, by the same predicate the join gate uses.
         *
         * `isMember = false` deliberately: they are not a member of a chat that does not exist, and
         * the question being asked is exactly the one the gate asks of a stranger.
         */
        val access: GroupAccess?
            get() = rules?.let {
                GroupAccess.evaluate(
                    isMember = false,
                    rules = it,
                    balances = balances,
                    // [rules] only ever builds a minimum balance — the form has no staff control —
                    // so nothing here reads this. It is passed rather than defaulted so that adding
                    // such a control has to come back and answer the question.
                    isStaff = false,
                )
            }

        /** Null rules can't be unsatisfied — an incomplete draft is blocked by [canCreate] instead. */
        val selfSatisfied: Boolean
            get() = access.let { it == null || it is GroupAccess.Eligible }

        val canCreate: Boolean
            get() = hasTitle && rules != null && selfSatisfied && processingState.isIdle

        /**
         * The amount shown in the `…` chip's place, when one was entered there.
         *
         * A custom amount has nowhere else to appear — the three chips are fixed — and an amount the
         * user set that the form does not show reads as the form having dropped it.
         */
        val customAmount: Fiat?
            get() = amount?.takeIf { it !in BalancePresets }
    }

    sealed interface Event {
        data class UploadPolicyLoaded(val policy: UploadPolicy) : Event

        /** A picture came back from the photo picker; it still has to be re-encoded. */
        data class OnImageSelected(val image: Uri) : Event

        /** The re-encoded copy is in the cache and is what gets uploaded. */
        data class OnImageCached(val image: Uri, val mimeType: String) : Event
        data object OnImageCleared : Event

        data class OnBalancesChanged(val balances: List<TokenWithBalance>) : Event
        data class OnRateChanged(val rate: Rate) : Event

        /** A mint was chosen on the currency sheet (node 10127:118100). */
        data class OnMintSelected(val mint: Mint) : Event

        /** A preset chip, or a confirmed custom keypad entry. Always USD. */
        data class OnAmountSelected(val amount: Fiat) : Event

        /** The custom keypad's confirm — converts and re-dispatches as [OnAmountSelected]. */
        data object ConfirmCustomAmount : Event

        /** Create was tapped. Retrying resumes the same attempt; see [GroupCreateAttempt]. */
        data object CreateRequested : Event

        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The chat exists. Carries it, because nothing else fetches it back. */
        data class ChatCreated(val chat: ChatMetadata) : Event
    }

    /**
     * The keypad behind the `…` chip.
     *
     * No ceiling and no floor: a group can ask for any balance, and unlike the minimum-to-chat fee
     * there is nothing stored to compare a change against. The creator's own balance is a bound on
     * *creating*, not on typing, and the form says so where the requirement is shown.
     */
    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        // The requirement is one amount, not a payment, so there is nothing to convert between
        // here — the currency the keypad enters in is the account's preferred one and is read back
        // into USD on confirm.
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_done)),
            actionStyle = ConfirmationStyle.Button,
            canChangeCurrency = false,
        ),
    )

    private val attempt = GroupCreateAttempt()

    init {
        exchange.observePreferredRate()
            .distinctUntilChanged()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)
                    ?.let { amountDelegate.onCurrencyChanged(it) }
                dispatchEvent(Event.OnRateChanged(rate))
            }
            .launchIn(viewModelScope)

        tokenCoordinator.tokenBalances
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnBalancesChanged(it)) }
            .launchIn(viewModelScope)

        blobStorage.policy
            .filterNotNull()
            .onEach { dispatchEvent(Event.UploadPolicyLoaded(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnImageSelected>()
            .mapNotNull { event ->
                // Re-encode inside the policy's dimension and byte caps, off the main thread. The
                // wording of a refusal is this screen's; the loop belongs to the preparer.
                when (val outcome = imagePreparer.prepare(
                    uri = event.image,
                    policy = stateFlow.value.uploadPolicy,
                    fileNamePrefix = "group_picture",
                )) {
                    is ImageUploadPreparer.Outcome.Prepared -> outcome.uri to outcome.mimeType
                    ImageUploadPreparer.Outcome.Unsupported -> {
                        rejectImage(
                            title = R.string.error_title_imageNotSupported,
                            message = R.string.error_description_imageNotSupported,
                        )
                        null
                    }

                    ImageUploadPreparer.Outcome.TooLarge -> {
                        rejectImage(
                            title = R.string.error_title_imageTooLarge,
                            message = R.string.error_description_imageTooLarge,
                        )
                        null
                    }

                    ImageUploadPreparer.Outcome.Unreadable -> null
                }
            }
            .flowOn(dispatchers.IO)
            .onEach { (cached, mime) -> dispatchEvent(Event.OnImageCached(cached, mime)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmCustomAmount>()
            .map { amountDelegate.state.value.enteredAmount }
            .onEach { entered ->
                if (entered <= 0.0) return@onEach
                // The keypad enters in the preferred currency; the rule is compared against a USD
                // balance, so it is stored in USD rather than in whatever was typed.
                val local = Fiat(entered, stateFlow.value.rate.currency)
                dispatchEvent(
                    Event.OnAmountSelected(local.convertingToUsdIfNeeded(stateFlow.value.rate))
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CreateRequested>()
            .onEach { create() }
            .launchIn(viewModelScope)
    }

    /**
     * A picked picture that never reached storage is only a re-encoded file in the cache, and
     * nothing else deletes it. Mirrors the photo-selection screen, including the successful upload
     * that makes the local copy redundant.
     */
    override fun onCleared() {
        discardPendingImage()
        super.onCleared()
    }

    private suspend fun create() {
        val state = stateFlow.value
        if (!state.processingState.isIdle) return

        val mint = state.mint ?: return
        val amount = state.amount ?: return
        if (!state.hasTitle) return

        // The self-satisfaction check the form already gates Create on, re-read here because the
        // balance behind it is a live flow and the tap is a separate moment from the render.
        if (!state.selfSatisfied) {
            announceNotSelfSatisfied(state.currencyName)
            return
        }

        val draft = GroupDraft(
            title = state.title,
            picture = state.image.dataOrNull,
            mint = mint,
            amount = amount,
        )

        // Minted before any network call, and held for as long as the draft is: every retry below —
        // the upload's and StartChat's alike — is the same attempt and carries the same key.
        val idempotencyKey = attempt.keyFor(draft)

        dispatchEvent(Event.UpdateProcessingState(loading = true))

        val picture = if (draft.picture == null) {
            null
        } else {
            attempt.picture ?: uploadPicture(draft.picture, state.imageMimeType)
                ?.also { attempt.rememberPicture(it) }
                ?: run {
                    dispatchEvent(Event.UpdateProcessingState())
                    return
                }
        }

        chatCoordinator.create(
            parameters = StartChatParameters.Group(
                title = draft.title,
                picture = picture,
                rules = state.rules,
            ),
            idempotencyKey = idempotencyKey,
        ).onSuccess { chat ->
            // The chat exists, so the key has done its job — a later Create is a second group.
            attempt.clear()
            // The cached local copy is redundant now that the blob is the chat's picture.
            discardPendingImage()
            dispatchSuccessThen(Event.UpdateProcessingState(success = true)) {
                dispatchEvent(Event.ChatCreated(chat))
                dispatchEvent(Event.UpdateProcessingState())
            }
        }.onFailure { cause ->
            // Deliberately not cleared: the attempt survives so the retry is the same attempt. If
            // the server did create the chat and only the response was lost, resending this key
            // returns that chat rather than making a second one.
            dispatchEvent(Event.UpdateProcessingState())
            announceCreateFailure(cause, stateFlow.value.currencyName)
        }
    }

    /** Uploads the prepared picture and returns its READY blob, or null having reported why not. */
    private suspend fun uploadPicture(uri: Uri, mimeType: String): BlobId? {
        val bytes = contentReader.readBytes(uri)
        if (bytes == null) {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_moderationFailed),
                message = resources.getString(R.string.error_description_moderationFailed),
            )
            return null
        }

        return blobStorage.upload(bytes = bytes, mimeType = mimeType)
            .onFailure { cause ->
                // A rejected picture is the picture's problem, so the pick is dropped — keeping it
                // on screen after refusing it invites the same upload again.
                discardPendingImage()
                announcePictureRejection(cause)
            }
            .getOrNull()
    }

    private fun discardPendingImage() {
        val pending = stateFlow.value.image.dataOrNull ?: return
        contentReader.removeFromCache(pending)
        dispatchEvent(Event.OnImageCleared)
    }

    private fun rejectImage(@StringRes title: Int, @StringRes message: Int) {
        dispatchEvent(Event.OnImageCleared)
        BottomBarManager.showAlert(
            title = resources.getString(title),
            message = resources.getString(message),
        )
    }

    private fun announceNotSelfSatisfied(currencyName: String?) {
        BottomBarManager.showAlert(
            title = resources.getString(R.string.error_title_groupRuleNotSelfSatisfied, currencyName.orEmpty()),
            message = resources.getString(R.string.error_description_groupRuleNotSelfSatisfied),
        )
    }

    private fun announcePictureRejection(cause: Throwable) {
        val rejection = (cause as? BlobRejectedException)?.rejection
        when (rejection?.reason) {
            RejectionReason.MODERATION -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_imageNotAllowed),
                message = resources.getString(moderationDescription(rejection.flaggedCategory)),
            )

            // Everything else is either a caller mistake the preparer should have caught
            // (UNSUPPORTED_TYPE, MISMATCHED_TYPE, TOO_LARGE, CORRUPT, PRIVACY_METADATA) or the
            // server's own (UNKNOWN, INTERNAL). Neither is something the user can act on beyond
            // picking a different picture, which is what the generic refusal says.
            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_moderationFailed),
                message = resources.getString(R.string.error_description_moderationFailed),
            )
        }
    }

    /**
     * The six `StartChatResponse.Result` arms, less `OK`.
     *
     * `RULES_NOT_SATISFIED` is not a failure of the form — it is the client-side check losing a race
     * with a balance that moved — so it reads the same as the pre-call refusal.
     */
    private fun announceCreateFailure(cause: Throwable, currencyName: String?) {
        when (cause) {
            is StartChatError.RulesNotSatisfied -> announceNotSelfSatisfied(currencyName)

            is StartChatError.TitleModerated -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupTitleNotAllowed),
                message = resources.getString(moderationDescription(cause.category)),
            )

            is StartChatError.InvalidRules -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupRulesInvalid),
                message = resources.getString(R.string.error_description_groupRulesInvalid),
            )

            is StartChatError.Denied -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupCreateDenied),
                message = resources.getString(R.string.error_description_groupCreateDenied),
            )

            // The blob was accepted by storage and refused by StartChat, so the picture is what has
            // to change. Dropping it here also drops the attempt's cached blob id with the draft.
            is StartChatError.PictureBlobNotAccepted -> {
                discardPendingImage()
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_imageNotAllowed),
                    message = resources.getString(R.string.error_description_imageNotAllowed),
                )
            }

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_groupCreateFailed),
                message = resources.getString(R.string.error_description_groupCreateFailed),
            )
        }
    }

    /** The app's existing moderation-category wording, as the username and photo screens map it. */
    @StringRes
    private fun moderationDescription(category: ModerationResult.FlaggedCategory): Int =
        when (category) {
            ModerationResult.FlaggedCategory.NONE ->
                R.string.error_description_imageNotAllowed

            ModerationResult.FlaggedCategory.OTHER ->
                R.string.error_description_profileNameNotAllowedFlaggedOther

            ModerationResult.FlaggedCategory.NSFW ->
                R.string.error_description_profileNameNotAllowedFlaggedNsfw

            ModerationResult.FlaggedCategory.IMPERSONATION ->
                R.string.error_description_profileNameNotAllowedFlaggedImpersonation

            ModerationResult.FlaggedCategory.MISLEADING ->
                R.string.error_description_profileNameNotAllowedFlaggedMisleading

            ModerationResult.FlaggedCategory.SPAM ->
                R.string.error_description_profileNameNotAllowedFlaggedSpam
        }

    companion object {
        @VisibleForTesting
        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.UploadPolicyLoaded -> { state -> state.copy(uploadPolicy = event.policy) }
                is Event.OnImageSelected -> { state ->
                    // Loading with the source uri behind it: the tile shows the pick immediately
                    // while the re-encode runs, then swaps to the cached copy.
                    state.copy(image = Loadable.Loading(event.image))
                }

                is Event.OnImageCached -> { state ->
                    state.copy(
                        image = Loadable.Loaded(event.image),
                        imageMimeType = event.mimeType,
                    )
                }

                Event.OnImageCleared -> { state ->
                    state.copy(image = Loadable.Loading(), imageMimeType = "")
                }

                is Event.OnBalancesChanged -> { state ->
                    state.copy(
                        balances = event.balances,
                        // The form opens with a mint already named (node 10127:118057), so one is
                        // seated here rather than left for the sheet. The largest holding is the
                        // one the creator can actually set a requirement in — picking anything
                        // else would open the form on a rule its own author fails.
                        //
                        // Balances under a cent are skipped because the currency sheet skips them
                        // too (`SelectTokenViewModel` filters `TokenPurpose.Balance` on
                        // `hasDisplayableValue`). Seating from a wider list than the picker offers
                        // would name a mint the picker cannot show, leaving no way back to it. A
                        // wallet holding only dust therefore seats nothing and shows the sheet's
                        // own "Select Currency" — an empty picker under a named currency would be
                        // the worse of the two.
                        mint = state.mint ?: event.balances
                            .filter { it.balance.hasDisplayableValue }
                            .maxByOrNull { it.balance.quarks }
                            ?.token?.address,
                    )
                }
                is Event.OnRateChanged -> { state -> state.copy(rate = event.rate) }
                is Event.OnMintSelected ->
                    { state -> state.copy(mint = event.mint, mintChosen = true) }
                is Event.OnAmountSelected -> { state -> state.copy(amount = event.amount) }
                Event.ConfirmCustomAmount -> { state -> state }
                Event.CreateRequested -> { state -> state }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = state.processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                is Event.ChatCreated -> { state -> state.copy(created = event.chat) }
            }
        }
    }
}
