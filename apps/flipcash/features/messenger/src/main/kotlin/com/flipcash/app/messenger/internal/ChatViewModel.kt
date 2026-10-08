package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.map
import com.flipcash.analytics.CashLinkChoice
import com.flipcash.analytics.GroupAccess as AnalyticsGroupAccess
import com.flipcash.analytics.GroupGateFunding
import com.flipcash.analytics.GroupInviteMethod
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.analytics.GroupInviteSource
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.gateMint
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.tokens.isReserve
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.link.CashCardTap
import com.flipcash.app.messenger.internal.link.ClaimReplyTargets
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.messenger.internal.mention.activeMentionToken
import com.flipcash.app.messenger.internal.mention.insertMention
import com.flipcash.app.messenger.internal.mention.mentionable
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.app.session.SettledClaim
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.chat.SampledChatter
import com.flipcash.app.messenger.internal.screens.profile.GroupBalanceRequirements
import com.flipcash.app.messenger.internal.screens.profile.GroupProfileStanding
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.isDmAddressable
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftSnapshot
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.media.MediaSendProgress
import com.flipcash.shared.chat.ui.media.MAX_STAGED_PHOTOS
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.chat.SpeakerBlock
import com.flipcash.shared.chat.speakerBlock
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.RosterSearchSource
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.applying
import com.flipcash.shared.chat.canReact
import com.flipcash.shared.chat.chatDraftOf
import com.flipcash.shared.chat.groupAccess
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.ui.media.photoBody
import com.flipcash.shared.chat.ui.media.photoContext
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.models.splitAroundLinkCard
import com.flipcash.shared.chat.reactions.ReactionError
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.flipcash.shared.chat.reactions.ReactionStripComposer
import com.flipcash.shared.chat.reactions.SelfReaction
import com.flipcash.shared.chat.readOnly
import com.flipcash.shared.chat.resolveCapabilities
import com.flipcash.shared.chat.ui.UndecryptableHint
import com.flipcash.shared.chat.ui.undecryptableHint
import com.flipcash.shared.chat.ui.detectMentions
import com.flipcash.shared.chat.ui.detectUrls
import com.flipcash.shared.chat.ui.linkableText
import com.flipcash.shared.chat.withinWindows
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.EmojiDrawability
import com.getcode.libs.emojis.reactions.RecentReactions
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TypingConstraints(
    val enabled: Boolean = false,
    // False until the async typing-enabled query (does this chat have a cash message?) has
    // answered at least once. The bottom bar keeps its layout invisible until this is true so it
    // never renders the default full-width state and then snaps to the resolved pill + input.
    val resolved: Boolean = false,
    val interval: Duration = 3.seconds,
    val timeout: Duration = 5.seconds,
)

@HiltViewModel
internal class ChatViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val mediaUploads: ChatMediaUploads,
    private val e2eePolicy: E2eePolicy,
    private val contactCoordinator: ContactCoordinator,
    private val contactPaymentDelegate: ContactPaymentDelegate,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val startChattingPayer: StartChattingPayer,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    private val clipboardManager: ClipboardManager,
    private val userFlags: UserFlagsCoordinator,
    private val linkCardClassifier: LinkCardClassifier,
    private val linkCardResolver: LinkCardResolver,
    private val cashLinkClaims: CashLinkClaims,
    private val chatCashLinks: ChatCashLinks,
    private val chatDraftStore: ChatDraftStore,
    private val recentReactionsStore: RecentReactionsStore,
    private val emojiCatalogLoader: EmojiCatalogLoader,
    private val toastController: SystemToastController,
    private val userProfileDataSource: UserProfileDataSource,
    private val rosterSearch: RosterSearchSource,
    private val featuredGroups: FeaturedGroupsStore,
    private val dispatchers: DispatcherProvider,
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    /**
     * A photo in the composer: its id in [ChatMediaUploads] and where it came from, for the
     * thumbnail. A camera shot staged at the shutter carries the frame taken then as [preview],
     * since [source] is not written yet.
     */
    data class StagedPhoto(val id: String, val source: Uri, val preview: Bitmap? = null)

    sealed interface ResolveState {
        data object Pending : ResolveState
        // The counterparty resolved to an on-chain address, so the send can proceed. The address
        // itself isn't held here — the payment delegates re-resolve it at send time (a cache hit),
        // keyed by the counterparty's phone number or user id.
        data object Resolved : ResolveState
        data object Failed : ResolveState
    }

    data class State(
        val separatorConfig: SeparatorConfig = SeparatorConfig.Continuous(),
        val chatId: ChatId? = null,
        val subject: ChatSubject? = null,
        /**
         * What this chat holds about the viewer, or null while nothing is known.
         *
         * On the chat rather than on the [ChatSubject] because it is not about who the
         * conversation is with: a DM is muted exactly the way a group is, and both profiles offer
         * the row. Carried whole rather than reduced to a muted flag — a timed mute lapses with
         * nothing sent to say so, so what is muted depends on when it is asked, and `isMutedAt`
         * needs the deadline this keeps.
         */
        val viewerState: ViewerState? = null,
        // The kind of DM this conversation is, resolved from the fast local contact lookup ahead of
        // the participant's server profile (which resolves over the network for tip DMs). Starts
        // UNKNOWN and settles to CONTACT_DM / TIP_DM as soon as the chat opens; the send button and
        // bottom bar read it to render the correct (condensed vs expanded) presentation immediately
        // instead of flashing the expanded white pill while a tip profile loads.
        val chatType: ChatType = ChatType.UNKNOWN,
        /** Whether [E2eePolicy] says this chat's messages are end-to-end encrypted. */
        val isEncrypted: Boolean = false,
        val chatInputState: TextFieldState = TextFieldState(),
        val typists: Set<ActiveTypist> = emptySet(),
        /** What the typing indicator draws ahead of its dots. See [typingAvatars]. */
        val typingAvatars: List<TypingAvatar> = emptyList(),
        val resolveState: ResolveState = ResolveState.Pending,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val isSelfTyping: Boolean = false,
        val typingConstraints: TypingConstraints = TypingConstraints(),
        val token: Token? = null,
        val limits: Limits? = null,
        val isAnonymous: Boolean = false,
        val cashSymbol: String = "$",
        // Transient "focus the message input" request. Set by OnStartMessageInput (dispatched when
        // returning from amount entry after a send, and on a post-tip chat open) and cleared by
        // OnMessageInputConsumed once the bottom bar has focused the field and shown the keyboard.
        // Kept as state (not a one-shot event) because eventFlow is replay-0: a request raised at
        // open would be missed by the bottom bar before it subscribes, whereas state is durable
        // until the input is actually composed and can consume it.
        val messageInputRequested: Boolean = false,
        /**
         * The message the selection bar is acting on, or `null` when the ordinary title bar is up.
         *
         * One message at a time: every capability the transcript resolves — copy, edit, delete —
         * applies to a single message, so a multi-selection would only ever be a bar with most of
         * its actions disabled.
         */
        val selection: ChatListItem.ContentBubble? = null,
        /**
         * Whether [selection] was made by a double tap, which presents only the quick reaction strip:
         * the lift and backdrop without the selection bar. Only meaningful while [selection] is set.
         */
        val reactionStripOnly: Boolean = false,
        /** The message the composer is editing, or `null` when it is composing a new one. */
        val editing: EditingMessage? = null,
        /**
         * The message the composer is citing, or `null` when it is composing an ordinary message.
         *
         * Mutually exclusive with [editing]: an edit takes the composer over with the message's own
         * body, so citing another message from inside one would send a reply that overwrites a
         * third. Unlike [EditingMessage] this stashes no draft — the draft is the reply.
         *
         * Cleared by the send handler rather than by the reducer: dispatchEvent reduces before it
         * emits, so a reducer that cleared it would empty it before the handler could read it.
         */
        val replyingTo: ChatQuote? = null,
        /** Photos staged in the composer, in the order they were added. Hidden, not dropped, while editing. */
        val stagedPhotos: List<StagedPhoto> = emptyList(),
        /** Where each staged photo's upload is; a chip with no entry yet is still being picked up. */
        val uploadStates: Map<String, ChatMediaUploadState> = emptyMap(),
        /**
         * Members matching the `@` word at the composer's cursor, for the mention picker. Empty
         * whenever the picker is closed: outside a group, with no `@` word at the cursor, or when
         * nothing matched. Only members with a username, since the username is what a pick inserts.
         *
         * Independent of [replyingTo] and [editing]: opening or cancelling a reply leaves it alone.
         */
        val mentionSuggestions: List<MemberMatch> = emptyList(),
        /**
         * A message the transcript has been asked to scroll to, held until the list consumes it.
         *
         * State rather than a one-shot event, for the reason [messageInputRequested] is: eventFlow
         * is replay-0, so a request raised while the list is recomposing would be dropped.
         */
        val jumpTarget: Long? = null,
        /**
         * How far back [jumpTarget] sits from the newest message, which bounds the walk that loads
         * it. Set alongside [jumpTarget] and cleared with it.
         */
        val jumpBudget: Int? = null,
        /**
         * Where the "N Unread Messages" divider sits, read once per visit from the viewer's stored
         * READ pointer. Never re-read: the list advances that pointer as the reader scrolls, and a
         * boundary that followed it would slide the divider away from what was new at open.
         */
        val unreadBoundary: UnreadBoundary = UnreadBoundary.Resolving,
        /** How many stored messages sit past the boundary, which bounds the walk that opens at it. */
        val unreadWalkBudget: Int = 0,
        /**
         * True while the delete confirmation is up.
         *
         * The sheet is modal, so nothing behind it should still read as the focus: the selected
         * message falls back behind the backdrop with the rest of the transcript until the sheet
         * closes, rather than sitting sharp and half-clipped at the sheet's own edge.
         */
        val confirmingDelete: Boolean = false,
        /**
         * The edit and delete windows the server publishes through `UserFlags`.
         *
         * Held in state rather than read straight off the coordinator because the reducer needs
         * it: a selection has to be narrowed to what is still open at the moment it is made, and
         * the reducer is where the selection is set.
         */
        val messagePolicy: MessagePolicy = MessagePolicy.Default,
        /**
         * The token a group's balance requirement names, and how to write it — "Jeffy", not its mint
         * and not its ticker. Null until the token cache has it, and null for any chat without such
         * a rule.
         */
        val ruleCurrency: RuleCurrency? = null,
        /**
         * What this viewer may do here. Null for anything that is not a group — a DM has no gate,
         * and rendering one from a default would blur every contact conversation in the app.
         */
        val groupAccess: GroupAccess? = null,
        /**
         * The speaker requirement standing between this viewer and speaking, or null when there is
         * none. Null for a DM, which has no rules, and until the balance and staff flag arrive, so
         * a group does not flash Reply away.
         */
        val speakerBlock: ChatRuleRequirement? = null,
        /**
         * Whether an unmet speaker rule also takes reactions away. False for `creator` alone, which
         * withholds posting (composer, Reply) and nothing else. See [SpeakerBlock].
         */
        val speakerBlocksReactions: Boolean = false,
        /**
         * Whether [speakerBlock] has been resolved at least once. Until it has, a null block means
         * "not known yet", not "nothing in the way", which is what [cashCardTap] needs to tell apart.
         */
        val speakerBlockResolved: Boolean = false,
        /**
         * The gate's Join button, same shape as [sendProgress]. Membership arrives from the roster
         * rather than from the join's own reply, so without this the button would sit unchanged for
         * the whole round trip and read as dead — which is what it looked like before it had one.
         */
        val joinProgress: LoadingSuccessState = LoadingSuccessState(),
        /** A leave is out, so the profile's Leave Chat shows a spinner and cannot be tapped again. */
        val leaving: Boolean = false,
        /**
         * What the group's profile pins and states about the viewer's balance, decided with the
         * live balance and rates. Null until they arrive, and for anything that is not a group.
         */
        val profileStanding: GroupProfileStanding? = null,
        /**
         * The name of each token a balance rule on the profile names, by the mint's bytes: the join
         * and chat rows can name different ones, so the single [ruleCurrency] is not enough. A mint
         * the cache has not resolved is absent.
         */
        val ruleTokens: Map<List<Byte>, RuleCurrency> = emptyMap(),
        /**
         * Who has been chatting in a public group, as of the last time its profile opened. Empty for
         * a private group and when the sample could not be had.
         */
        val chatters: List<SampledChatter> = emptyList(),
        /**
         * The quick strip's entries for [selection], recomposed whenever the selected message
         * changes — empty while nothing is selected, while the selected message can't be reacted
         * to, or before the catalog/recents have loaded for the first selection of this session.
         */
        val quickReactionStrip: List<ReactionStrip.Entry> = emptyList(),
        /**
         * What [quickReactionStrip] is built from besides the message's own reactions, or null
         * before the first load. Held here so the selection reducer can build the strip in the same
         * update that lifts the bubble.
         */
        val quickReactionInputs: QuickReactionInputs? = null,
    ) {
        /**
         * The DM counterparty, or `null` for a group.
         *
         * Kept as a derived property so the paths that predate [ChatSubject] — the quote accent,
         * the tip recipient flow and the payment branches — keep reading what they always read.
         * None of them is reachable from a group, which is exactly what a `null` says here.
         */
        val participant: ChatParticipant?
            get() = subject?.asParticipant()

        // Opening the participant's profile (the entry point to blocking) is the tip arm's answer
        // alone. Asking the subject rather than comparing chat types means a new arm has to say.
        val canViewProfile: Boolean
            get() = subject?.canViewProfile == true

        /** What the selection bar may offer, straight from what the transcript already resolved. */
        val selectionCapabilities: Set<MessageCapability>
            get() = selection?.capabilities.orEmpty()

        /**
         * Whether this conversation is being read from outside the group: a group the viewer has
         * not joined, or one whose membership is not known yet.
         */
        val isOutsideGroup: Boolean
            get() = subject is ChatSubject.Group && subject.isMember != true

        /**
         * Whether the server will hand this viewer the group's transcript in full without their
         * joining it.
         *
         * The contract's line (`messaging.v1.ViewMode`): a non-member who satisfies a group's
         * listener rules may read it in full, and a non-member of a group with no listener rules may
         * not read it at all. So [GroupAccess.Eligible] alone is not enough — it is also the answer
         * for a group with no rules, where there is nothing to read.
         *
         * Every unknown answers no. A null [groupAccess] is the balance or the staff flag not having
         * arrived, [GroupAccess.Undetermined] is a balance rule waiting on an exchange rate, and a
         * null membership is a group hydrated by id that GetChat could not place the viewer in;
         * reading any of them as eligible would show a transcript the viewer may not be owed.
         */
        val readsFromOutside: Boolean
            get() = subject is ChatSubject.Group &&
                subject.isMember == false &&
                groupAccess == GroupAccess.Eligible &&
                subject.rules?.listener.orEmpty().isNotEmpty()

        /**
         * Whether the transcript is blurred, and the placeholder stands in for it when empty.
         *
         * Read off the subject first, not off [groupAccess] alone, because this one has to be right
         * on the frame the group first renders. [groupAccess] arrives through the balance and staff
         * flows, so it is null for as long as those take — and a withheld transcript that starts
         * unblurred and then blurs has already shown what it was withholding. So the blur goes on
         * with the subject and only comes off once the access says [GroupAccess.Eligible]: an
         * eligible viewer watches it fade, which is the direction that leaks nothing.
         *
         * [GroupAccess.Blocked] keeps it on, and a balance that drops while the chat is open turns
         * [GroupAccess.Eligible] into [GroupAccess.Blocked] and puts it back.
         *
         * Not held by a succeeded join. The only viewer who can join is an eligible one, who is
         * already reading the transcript sharp, and a group with no listener rules unblurring as the
         * roster confirms the join is the same sharp-transcript-over-gate state an eligible viewer
         * sits in.
         *
         * Mirrors iOS `ConversationGatePresentation.obscuresTranscript`.
         */
        val obscuresTranscript: Boolean
            get() = isOutsideGroup && !readsFromOutside

        /** Whether the chat's speaker rules let this viewer speak. See [speakerBlock]. */
        val canSpeak: Boolean
            get() = speakerBlock == null

        /**
         * Whether a member who may not speak sees the read-only panel in place of the composer.
         * Distinct from [replacesComposer], which is about the viewer being outside the group: a
         * viewer outside has the Join gate, and speaking is a question asked only of a member.
         *
         * Mirrors iOS `ConversationGatePresentation.readOnly`.
         */
        val isReadOnlySpeaker: Boolean
            get() = subject != null && !isOutsideGroup && !replacesComposer && speakerBlock != null

        /**
         * Whether the gate stands where the composer does. Every viewer outside the group, eligible
         * or not: reading a group from outside is not posting in it.
         *
         * A succeeded join keeps it true through the button's checkmark, so the roster — which can
         * confirm membership on the next frame — cannot cut the confirmation short by swapping the
         * composer in under it.
         *
         * Mirrors iOS `ConversationGatePresentation.replacesComposer`.
         */
        val replacesComposer: Boolean
            get() = joinProgress.success || isOutsideGroup

        /**
         * Whether the join gate draws its panel where the composer was. False while the gate is
         * [GroupAccess.Undetermined]: a panel would offer a Join the rate may yet take back, and
         * the composer is still withheld by [replacesComposer], so the bar is simply empty until
         * the rate settles it. A join already under way keeps its panel so its progress stays
         * visible.
         *
         * Mirrors iOS `ConversationBottomBar`, which draws nothing for `.undetermined`.
         */
        val showsGatePanel: Boolean
            get() = replacesComposer &&
                !(groupAccess == GroupAccess.Undetermined && joinProgress.isIdle)

        /** The staged photos the composer shows: none while editing, which is text only. */
        val composerPhotos: List<StagedPhoto>
            get() = if (editing != null) emptyList() else stagedPhotos

        /** Whether the composer takes photos: the chat is known and no edit is in progress. */
        val acceptsMedia: Boolean
            get() = chatId != null && editing == null

        /**
         * A photo whose upload failed for good (one a retry can't fix) blocks sending until it is
         * removed. A retryable failure, such as being offline, is sent anyway: the send uploads it
         * again and the bubble shows the result.
         */
        val hasFailedPhoto: Boolean
            get() = composerPhotos.any { (uploadStates[it.id] as? ChatMediaUploadState.Failed)?.retryable == false }

        /** Whether the send control does anything: text or a photo to send, and no failed photo. */
        fun canSendComposer(hasText: Boolean): Boolean =
            (hasText || composerPhotos.isNotEmpty()) && !hasFailedPhoto

        /**
         * What a tap on a cash card in this transcript does.
         *
         * Only a member collects. An eligible non-member reads the transcript sharp and so can see
         * and tap a card, but the cash was sent to the group, so the card tells them to join
         * instead. A blurred transcript has nothing to tap; it falls on the same side because it is
         * also outside the group.
         *
         * A member collects only if the rules let them chat. The test is [speakerBlocksReactions]
         * rather than [canSpeak]: it is true for an unmet balance, staff or `never` rule and any
         * unmet listener rule, and false for `creator` alone, so members of a broadcast group can
         * still collect what its creator posts. It is also false for a speaker rule this client
         * does not understand, which collects: unknown rules fail open here as they do elsewhere.
         *
         * A group whose block has not resolved yet refuses, unlike the composer, which shows until
         * it does: a claim cannot be taken back once the rules arrive and say no. A DM is not held
         * for it, since most carry no rules and none is a place cash is gated.
         *
         * The claim is only answered with a thank-you when the viewer can post, because the
         * thank-you is a message they post. A deactivated DM has no composer and a creator-only
         * group refuses its members' messages, so a reply from either would be refused.
         *
         * Mirrors iOS, which refuses at `.join` and when `ConversationGate.allowsReactions` is false
         * or the rules have not loaded, and records the tap for a reply only at `.open`.
         */
        val cashCardTap: CashCardTap
            get() = when {
                isOutsideGroup -> CashCardTap.JoinToCollect
                speakerBlocksReactions -> CashCardTap.ChatToCollect
                subject is ChatSubject.Group && !speakerBlockResolved -> CashCardTap.ChatToCollect
                else -> CashCardTap.Collect(thanks = !isAnonymous && !replacesComposer && canSpeak)
            }

        /**
         * The link that invites someone into this group, or `null` for a DM. Built from the chat's
         * id — there is no invite RPC, and [Linkify] is the one place that decides the link's shape.
         * Offered to anyone looking at the group, member or not: the link is the chat's id and gives
         * a recipient nothing the chat's own page would not.
         */
        val shareableGroupInviteUrl: String?
            get() = (subject as? ChatSubject.Group)?.let { Linkify.groupChatInvite(it.chatId) }
    }

    /**
     * An edit in progress.
     *
     * [stashedDraft] is whatever the composer held when the edit began; leaving edit mode — by
     * confirming, cancelling, or backing out — puts it back, so starting an edit never costs the
     * user a half-written message.
     */
    data class EditingMessage(
        val messageId: Long,
        val originalText: String,
        val stashedDraft: String,
    )

    sealed interface Event {
        data class OnChatOpened(val identifier: ChatIdentifier) : Event
        data class OnContactFound(val contact: DeviceContact): Event
        data class OnTipUserResolved(val userId: ID, val profile: UserProfile): Event
        data object OnTipDmDetected : Event

        /** The chat this screen is showing turned out to be a group, with this metadata. */
        data class OnGroupResolved(val membership: ChatMembership) : Event

        /** The token cache learned the currency behind the group's balance requirement. */
        data class OnRuleCurrencyResolved(val currency: RuleCurrency?) : Event

        /** The gate re-decided, because membership, the rules, or the balance moved. */
        data class OnGroupAccessResolved(val access: GroupAccess) : Event
        data class OnSpeakerBlockResolved(val block: SpeakerBlock?) : Event

        /** The gate's "Join Chat" button. */
        data object JoinChat : Event

        /**
         * The Join button's own state. Success is dispatched for the length of the checkmark and
         * then cleared, which is what releases the gate to the composer.
         */
        data class JoinStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The invite sheet's "Copy Invite Link" row. */
        data object CopyInviteLink : Event

        /** The invite sheet was presented, from the transcript's CTA or the group profile. */
        data class InviteSheetOpened(val source: GroupInviteSheetSource) : Event

        /** The invite sheet's "Send Invite Link" row, which hands the link to the share sheet. */
        data object InviteLinkShared : Event

        /** The gate's buy or add-cash button. The navigation is the screen's; this is the record. */
        data class GateFundingTapped(val method: GroupGateFunding) : Event

        /** The group's profile was pushed from the transcript. */
        data object GroupInfoOpened : Event

        /** A group invite card in the transcript was tapped through to another group. */
        data object InviteCardFollowed : Event

        /** The group profile's "Leave Chat" row, which puts the confirmation up. */
        data object LeaveChat : Event

        /** The user confirmed the leave. */
        data object LeaveConfirmed : Event

        /** The leave went through, so whatever is showing the group's profile should close. */
        data object LeftChat : Event

        /** The leave did not go through; the profile stays up and the button comes back. */
        data object LeaveFailed : Event

        /** The group's profile came on screen: sample its chatters and fill in a missing cover. */
        data object GroupProfileOpened : Event
        data class OnChattersLoaded(val chatters: List<SampledChatter>) : Event
        data class OnProfileStandingResolved(val standing: GroupProfileStanding?) : Event
        data class OnRuleTokensResolved(val tokens: Map<List<Byte>, RuleCurrency>) : Event

        /** This chat's viewer state moved, from the stream or from the viewer's own request. */
        data class OnViewerStateResolved(val viewerState: ViewerState?) : Event

        data class OnEncryptionResolved(val isEncrypted: Boolean) : Event

        data class OnCurrencySymbolUpdated(val symbol: String): Event
        data class ChatFound(val chatId: ChatId) : Event
        data object OnSendCash: Event
        data object OnStartMessageInput: Event
        data object OnStopMessageInput: Event
        data object OnMessageInputConsumed: Event
        data class TypistsUpdated(val typists: Set<ActiveTypist>) : Event
        data class TypingAvatarsUpdated(val avatars: List<TypingAvatar>) : Event
        data object ResolveCompleted : Event
        data object ResolveFailed : Event

        data object SendMessage : Event

        /** Stages [uris] as photos, up to [MAX_STAGED_PHOTOS] in all. [captured] ones are camera files to delete once safe. */
        data class StagePhotos(val uris: List<Uri>, val captured: Boolean = false) : Event
        data class PhotoStaged(val photo: StagedPhoto) : Event

        /**
         * The shutter was pressed: stage the shot now, as [preview], so its chip is there for the
         * camera to land on; reading [uri] waits for [CaptureFinished].
         */
        data class CaptureStarted(val uri: Uri, val preview: Bitmap?) : Event

        /** The shot for [uri] was written, or, when not [saved], failed and leaves the composer. */
        data class CaptureFinished(val uri: Uri, val saved: Boolean) : Event
        data class RemovePhoto(val id: String) : Event
        data class RetryPhoto(val id: String) : Event
        data class UploadStatesChanged(val states: Map<String, ChatMediaUploadState>) : Event

        /** The staged photos went into a send; they leave the composer without being removed. */
        data class PhotosSent(val ids: Set<String>) : Event

        /** A send of [photos] did not go; they return to the composer. */
        data class PhotosRestored(val photos: List<StagedPhoto>) : Event
        data class RetryMessage(val pendingId: String?, val content: MessageContent) : Event

        data object NavigateToAmountEntry : Event

        data object PresentDepositOptions : Event
        data class OpenScreen(val route: AppRoute, val asSheet: Boolean = false): Event

        /** The reader tapped a mention of [username] in the transcript. */
        data class MentionTapped(val username: String) : Event

        /**
         * Where a tapped mention resolved to, for the screen to open. Only the destinations that
         * open something: an unclaimed handle or a failed lookup is a dialog raised here.
         */
        data class OpenMention(val destination: MentionDestination) : Event
        data object OnConfirmRequested : Event

        data class OnSendRequested(
            val amount: Fiat,
            val token: Token,
        ) : Event
        data class SendStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event
        data class SendComplete(val amount: Fiat) : Event

        data object OnSelfTypingStarted : Event
        data object OnSelfTypingStill : Event
        data object OnSelfTypingStopped : Event
        data class TypingEnabled(val enabled: Boolean) : Event

        data class TokenUpdated(val token: Token) : Event
        data class LimitsChanged(val limits: Limits?) : Event
        data class AdvanceReadPointer(val messageId: Long) : Event
        data class ChatDeactivated(val isReadOnly: Boolean) : Event
        data class MessagePolicyChanged(val policy: MessagePolicy) : Event

        /** Selects [bubble], or leaves selection mode if it is already the selected one. */
        data class ToggleMessageSelection(val bubble: ChatListItem.ContentBubble) : Event

        /**
         * Selects [bubble] for the quick reaction strip alone, as a double tap does; nothing when it
         * can't take a reaction once narrowed to what is open now.
         */
        data class PresentReactionStrip(val bubble: ChatListItem.ContentBubble) : Event
        data object ClearMessageSelection : Event

        // The message actions carry what they act on rather than reading it back off the selection:
        // the reducer runs before the handlers do, so an action that dismisses the selection bar
        // would otherwise have cleared its own subject before the handler saw it.
        data class CopyMessage(val text: String) : Event
        data class EditMessage(val messageId: Long, val text: String) : Event
        data class DeleteMessage(val messageId: Long) : Event

        // Carries nothing because it does nothing but dismiss the bar: report is its own top-level
        // flow, and the bar's caller has already captured the message to push the route with.
        data object ReportRequested : Event

        data object SubmitEdit : Event
        data object CancelEdit : Event
        data object EditingEnded : Event

        /**
         * Asks for a reply to [bubble]. Both entry points — the selection bar and the swipe — land
         * here rather than on [ReplyToMessage], because turning a bubble into a citation needs the
         * stored message, and that read belongs in one place.
         */
        data class ReplyRequested(val bubble: ChatListItem.ContentBubble) : Event

        /** Opens the composer's reply strip on an already-resolved citation. */
        data class ReplyToMessage(val quote: ChatQuote) : Event
        data object CancelReply : Event

        /** The mention picker's results for the `@` word now at the cursor; empty closes it. */
        data class OnMentionSuggestions(val matches: List<MemberMatch>) : Event

        /** Replaces the `@` word at the cursor with [match]'s username and a space. */
        data class PickMention(val match: MemberMatch) : Event

        /**
         * Toggles the viewer's reaction with [emoji] on [messageId] — a tap on a pill, on the
         * quick strip above a selected bubble, or a pick from the full picker. When it came from
         * the strip, [clearsSelection] takes the bubble out of selection mode the same tap it acts
         * on; a tap on the pill row under a bubble that isn't selected leaves selection untouched.
         */
        data class ToggleReaction(
            val messageId: Long,
            val emoji: String,
            val clearsSelection: Boolean = false,
        ) : Event

        /** Internal: the quick strip's catalog and recents (re)loaded. */
        data class QuickReactionInputsLoaded(val inputs: QuickReactionInputs) : Event

        /** Internal: the viewer's own reactions on the selected message moved. */
        data class SelectionReactionsChanged(
            val messageId: Long,
            val selfReactions: List<SelfReaction>,
        ) : Event

        /**
         * The transcript's own refresh-on-paging hook has ids it wants reaction state refreshed
         * for — see [ReactionRefreshPlanner] and the [ChatAction.RefreshReactionIds] that carries
         * this down from `MessageList`. Not a reader gesture, so it changes no visible state.
         */
        data class RefreshReactionIds(val messageIds: List<Long>) : Event

        /**
         * The reader tapped a cash voucher in this transcript, naming the link.
         *
         * Only the name: the screen opens the link through the URL handler right after
         * dispatching this, and nothing here claims anything. Dispatched only when
         * [State.cashCardTap] lets the reader collect. See [initClaimReplies].
         */
        data class CashLinkOpened(val entropy: String) : Event

        /**
         * The reader tapped a cash voucher they cannot collect from here, per
         * [State.cashCardTap]. The link was not opened; this only tells them why, which [tap] names.
         */
        data class CashLinkRefused(val tap: CashCardTap) : Event

        /** Asks the transcript to scroll to [messageId] — a tap on a quote. */
        data class JumpToMessage(val messageId: Long) : Event

        /** The same request, once the walk's bound is known. */
        data class JumpResolved(val messageId: Long, val budget: Int) : Event

        /** The chat's unread boundary, read once when its id became known. */
        data class UnreadBoundaryResolved(val boundary: UnreadBoundary, val walkBudget: Int) : Event
        data object JumpConsumed : Event
    }

    /**
     * The chat the open handler last ran its first-open work for. Not [State.chatId]: a chat id
     * opened by id is in state from the moment the open is dispatched, before that work has run.
     */
    private var openedChatId: ChatId? = null

    /**
     * Where the transcript's page caches collect. Off main, so the first page's load and mapping
     * don't queue behind the screen's first frames.
     */
    private val pagingScope = CoroutineScope(viewModelScope.coroutineContext + dispatchers.Default)

    /**
     * Quoted messages read for the current page generation, keyed by id; empty when not stored.
     * Any input to [mappedMessages] re-maps every loaded row, and without this each re-map read
     * every quote again. A new generation clears it, so an edit to a quoted message shows.
     */
    private val quotedMessages = ConcurrentHashMap<Long, Optional<ChatMessage>>()
    private var quotedGeneration: PagingData<ChatMessage>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageStream = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeMessagesPaged(it) }
        // Cached here rather than after the mapping below so the overlay composes over the page
        // cache: an edit or delete awaiting the server re-runs the mapping without re-fetching.
        .cachedIn(pagingScope)

    /** Where the Encrypted marker goes: read from the transcript, never from `use_e2ee`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val oldestEncryptedId = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeOldestEncryptedMessageId(it) }
        .distinctUntilChanged()

    /** Edits and deletes the server has not answered yet, composed over the stored transcript. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingMutations = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observePendingMutations(it) }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val otherReadPointer = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeOtherReadPointer(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Re-runs the transcript mapping when the windows change, so a message resolved under the
     * defaults (both open, before the flags arrive) narrows as soon as the server's answer lands.
     */
    private val messagePolicy = stateFlow.map { it.messagePolicy }.distinctUntilChanged()

    /**
     * Re-runs the transcript mapping when the viewer joins or leaves, so a message resolved while
     * they were reading from outside picks up Reply the moment the join lands, rather than on the
     * next page.
     */
    private val viewerCanPost = stateFlow.map { !it.isOutsideGroup }.distinctUntilChanged()

    /**
     * Whether the chat's speaker rules let the viewer speak, alongside [viewerCanPost] (which is
     * membership). Reply and reactions read it on every message.
     */
    private val viewerCanSpeak = stateFlow.map { it.canSpeak }.distinctUntilChanged()

    /** Whether the speaker rules leave reactions open; wider than [viewerCanSpeak], see `speakerBlocksReactions`. */
    private val viewerCanReact = stateFlow.map { !it.speakerBlocksReactions }.distinctUntilChanged()

    /** Whether the viewer is reading a group they have not joined, which shows its photos as BlurHash only. */
    private val viewerPreviewing = stateFlow.map { it.isOutsideGroup }.distinctUntilChanged()

    private data class ViewerGates(
        val canPost: Boolean,
        val canSpeak: Boolean,
        val canReact: Boolean,
        val previewing: Boolean,
    )

    /**
     * Live reaction overrides for the open chat — see [ReactionOperations.observeChatReactions].
     * A message missing here falls back to `MessageReactions.from(message.reactions)` in
     * [mappedMessages], per that function's contract.
     *
     * Held as a [StateFlow] (Eagerly, like [senderProfiles]) rather than left cold: the toggle
     * handler needs a synchronous read of "is this already self-reacted" to decide whether a tap
     * is an add worth ranking for the quick strip, and a cold flow would mean starting a second
     * collection just to answer that one question.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val reactionOverlay: StateFlow<Map<Long, MessageReactions>> = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeChatReactions(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Null for a DM, a map for a group — the distinction the transcript needs, because a DM's
     * counterparty is already named in the title bar and attributing each of their bubbles would be
     * noise. `null` rather than an empty map so the difference survives: an empty map in a group is
     * a real state (nothing resolved yet) and has to keep asking for profiles, where a DM must not
     * ask at all.
     *
     * Two sources behind it, because the roster is a truncated subset — members come from it, and
     * anyone it omits (a sender who left, a member past the cap) is asked for one profile at a time.
     * See SenderResolver.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val senderProfiles: StateFlow<Map<String, UserProfile>?> =
        stateFlow.map { it.chatType == ChatType.GROUP }
            .distinctUntilChanged()
            .flatMapLatest { isGroup ->
                // Starts from the last read so the transcript's first mapping already has the
                // names; waiting on the fresh query mapped the first page twice.
                if (isGroup) {
                    chatCoordinator.observeSenderProfiles()
                        .onStart { chatCoordinator.currentSenderProfiles()?.let { emit(it) } }
                } else flowOf(null)
            }
            // Held rather than cold so a tap on a sender's picture can read the profile that drew
            // it. The transcript is the only thing that asks for these, so the map the paging
            // stream is using is the one to answer from — looking the sender up again would be a
            // second source for an identity already on screen.
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The member behind [userId], as a participant a profile screen can be opened on.
     *
     * Null until the transcript has resolved them, which it has by the time their picture is
     * drawn — the picture is what this is reached from.
     */
    fun memberParticipant(userId: ID): ChatParticipant.TipUser? =
        senderProfiles.value?.get(userId.hexEncodedString())
            ?.let { ChatParticipant.TipUser(userId, it) }

    /**
     * Opens the chat [identifier] names, from [init], so the state the screen first reads already
     * has it.
     *
     * A group already in the feed is drawn from the feed row in the same update, so the first
     * frame has its name and picture instead of a placeholder, and the transcript starts on its
     * sender profiles without waiting for Room to emit the stored metadata. The feed only holds
     * chats you are in, so the membership is known. Room's emission replaces the seed as usual.
     */
    private fun openChat(identifier: ChatIdentifier) {
        if (identifier is ChatIdentifier.ByChatId && stateFlow.value.subject == null) {
            chatCoordinator.state.value.feed
                ?.firstOrNull { it.chatId == identifier.chatId && it.type == ChatType.GROUP }
                ?.let { dispatchEvent(Event.OnGroupResolved(ChatMembership(metadata = it, isMember = true))) }
        }
        dispatchEvent(Event.OnChatOpened(identifier))
    }

    /**
     * What the quick strip is built from, other than the message's own reactions. Held in [State]
     * ahead of a long-press so the selection reducer can build the strip in the same update that
     * lifts the bubble; built on demand, the strip trailed the lift by the catalog's glyph check and
     * a DataStore read. Reloaded at init and after each toggle, which is what moves recents.
     */
    data class QuickReactionInputs(
        val recentStats: Map<String, RecentReactions.Usage>,
        val catalogFill: List<String>,
        val undrawable: Set<String>,
    )

    /**
     * How a drawn card reaches the lookup. Provided to the transcript, read by `LinkCardView`.
     *
     * The resolver rather than the transcript is what a card asks, so the type narrows to the part
     * of it `chat-ui` is allowed to see — the query and the signal that an answer moved, not the
     * eviction that produces the signal, which stays this screen's to decide.
     */
    val linkCardResolution: LinkCardResolution get() = linkCardResolver

    /**
     * Where a claim goes back to, for a voucher tapped in this transcript.
     *
     * Held here rather than carried down to the bubble: neither `TextBubble` nor `BareLinkCard` is
     * given a message id, and the card is drawn from several places inside them, so threading one
     * through would touch every call site to answer a question this side already knows.
     */
    private val claimReplyTargets = ClaimReplyTargets()

    /** Progress of the photos this device is sending, for the transcript's overlays. */
    val mediaSendProgress: StateFlow<Map<String, MediaSendProgress>> =
        chatCoordinator.observeMediaSendProgress()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The transcript's rows before separators. Cached so a change to the unread boundary or the
     * separator config re-runs only [messages]' separator pass, not the per-message lookups here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mappedMessages: Flow<PagingData<ChatListItem.ContentBubble>> =
        combine(
            messageStream,
            pendingMutations,
            messagePolicy,
            senderProfiles,
            combine(viewerCanPost, viewerCanSpeak, viewerCanReact, viewerPreviewing, ::ViewerGates),
        ) { pagingData, mutations, policy, profiles, (canPost, canSpeak, canReactToMessages, previewing) ->
            if (pagingData !== quotedGeneration) {
                quotedGeneration = pagingData
                quotedMessages.clear()
            }
            pagingData.flatMap { stored ->
                val message = stored.applying(mutations[stored.messageId])
                message.content.flatMapIndexed { index, content ->
                    val enriched = if (content is MessageContent.Cash && content.tokenName.isBlank()) {
                        val token = tokenCoordinator.getTokenMetadata(content.mint).getOrNull()?.token
                        if (token != null) {
                            content.copy(tokenName = token.name, tokenImageUrl = token.imageUrl)
                        } else content
                    } else content

                    // Resolved here, next to the token-metadata lookup, because this is the one
                    // place in the transcript that already does async per-item work. A citation of
                    // a message this device never stored resolves to null, and the bubble renders
                    // its body with no panel rather than an error.
                    val quote = (content as? MessageContent.Reply)?.let { reply ->
                        stateFlow.value.chatId
                            ?.let { chatId ->
                                quotedMessages.getOrPut(reply.repliedMessageId) {
                                    Optional.ofNullable(chatCoordinator.getMessage(chatId, reply.repliedMessageId))
                                }.orElse(null)
                            }
                            ?.toQuote()
                    }

                    // Built from the same detection pass the bubble underlines with, so the
                    // link that becomes a card is always one the reader can see is a link.
                    //
                    // Classified only. The card comes out of here in its loading state and runs
                    // its own lookup -- see `LinkCardResolution`. This pass used to await that,
                    // which meant a chat painted nothing until every link in the first window had
                    // been round-tripped, and that every link in every mapped message was queried
                    // whether or not the reader ever scrolled to it.
                    val linkableText = enriched.linkableText()
                    val links = linkableText?.let(::detectUrls).orEmpty()
                    val linkCard = linkCardClassifier.firstCard(links)

                    // Detected over the whole text, beside the links, so a handle inside a link
                    // stays the link's. Nothing is looked up until one is tapped.
                    val mentions = linkableText?.let { detectMentions(it, links) }.orEmpty()

                    // Noted while the voucher and the message it came on are in the same hand;
                    // see [ClaimReplyTargets]. Skipped for the reader's own messages, which is what
                    // makes "do not thank yourself for your own link" structural rather than a
                    // check at send time.
                    if (!message.isFromSelf) {
                        (linkCard as? LinkCard.Cash)
                            ?.let { claimReplyTargets.note(it.entropy, message.messageId) }
                    }

                    val receiptStatus = if (message.isFromSelf) {
                        when (message.deliveryStatus) {
                            DeliveryStatus.SENDING -> ReceiptStatus.SENDING
                            DeliveryStatus.FAILED -> ReceiptStatus.FAILED
                            DeliveryStatus.SENT -> ReceiptStatus.SENT
                        }
                    } else null

                    // Stored fallback only — see the reactionOverlay pass below, which fills in a
                    // live override where one exists.
                    val storedReactions = MessageReactions.from(message.reactions)

                    // A message the viewer sent needs no attribution, and neither does a DM —
                    // `profiles` is null for one. Asking for a profile that is missing, or cached
                    // with no name, is what keeps a sender from showing as a blank name for the
                    // rest of the session; the resolver dedupes, so asking once per page is asking
                    // once.
                    val sender = profiles?.let { resolved ->
                        message.senderId?.takeIf { !message.isFromSelf }?.let { senderId ->
                            val profile = resolved[senderId.hexEncodedString()]
                            if (profile == null || profile.displayName.isBlank()) {
                                chatCoordinator.requestSenderProfile(senderId)
                            }
                            if (profile == null) {
                                null
                            } else {
                                SenderIdentity(
                                    userId = senderId,
                                    displayName = nameOrHandle(profile.displayName, profile.handle)
                                        ?: resources.getString(R.string.title_unnamedUser),
                                    // Only a name the person chose gives initials; "Flipcash User"
                                    // would put the same "FU" on every one of them.
                                    initialsName = nameOrHandle(profile.displayName, profile.handle),
                                    picture = profile.profilePicture,
                                )
                            }
                        }
                    }

                    ChatListItem.ContentBubble(
                        messageId = message.messageId,
                        contentIndex = index,
                        content = enriched,
                        isFromSelf = message.isFromSelf,
                        timestamp = message.timestamp,
                        receiptStatus = receiptStatus,
                        pendingClientIdHex = message.pendingClientIdHex,
                        isEdited = message.lastEditedTs != null,
                        // A null author is a moderation removal, which reads as someone else's.
                        deletedByViewer = (enriched as? MessageContent.Deleted)?.deletedBy
                            ?.let { it == userManager.accountId } == true,
                        // Resolved once, here, so no menu re-derives it: a later group-role
                        // taxonomy becomes another input to the resolver rather than a branch at
                        // each action site.
                        capabilities = resolveCapabilities(
                            message,
                            policy,
                            canPost = canPost,
                            canSpeak = canSpeak,
                        ),
                        quote = quote,
                        sender = sender,
                        // Independent of the profile lookup above: the runs have to break by
                        // author from the first frame, and the map that names the authors lands
                        // after the first page does.
                        senderId = message.senderId?.takeIf { !message.isFromSelf },
                        linkCard = linkCard,
                        mentions = mentions,
                        // `splitAroundLinkCard` keeps these pills on the last row only.
                        reactionPills = storedReactions.pills,
                        selfReactions = storedReactions.selfReactions,
                        canReact = canReact(message, canSpeak = canPost && canReactToMessages),
                        photo = stateFlow.value.chatId?.takeIf { content.photoBody() != null }
                            ?.let { chatId -> message.photoContext(chatId, previewing) },
                        undecryptableHint = undecryptableHint(
                            encryption = message.encryption,
                            isFromSelf = message.isFromSelf,
                            // Only a DM is encrypted, so the sender of an incoming one is the
                            // participant.
                            senderName = stateFlow.value.participant?.name
                                ?: resources.getString(R.string.title_unnamedUser),
                        ) ?: UndecryptableHint.UpdateApp,
                    )
                        // A carded link takes a row of its own, with the prose either side of it
                        // on rows above and below. Reversed because the list is: this page runs
                        // newest-first under reverseLayout, so the row drawn lowest goes first.
                        .splitAroundLinkCard()
                        .asReversed()
                }
            }
        }
        // A live reaction override — an in-flight tap, or a refreshed/streamed confirmed state —
        // beats the stored fallback baked in above. Combined as its own pass over the already-built
        // pages rather than folded into the combine above: it needs nothing else that combine reads
        // (only the message id already on each row), and keeping it separate means a reaction
        // update re-maps pills without re-running sender resolution, link classification or the
        // policy/capability pass over every row.
        .let { pages ->
            combine(pages, reactionOverlay) { data, overlay ->
                if (overlay.isEmpty()) return@combine data
                data.map { bubble ->
                    val live = overlay[bubble.messageId] ?: return@map bubble
                    bubble.copy(
                        // Only the row the "Edited" marker also lands on carries the pill row, so a
                        // split message shows one set of pills, not one per row.
                        reactionPills = if (bubble.isLastRow) live.pills else bubble.reactionPills,
                        selfReactions = live.selfReactions,
                    )
                }
            }
        }.cachedIn(pagingScope)

    /**
     * The transcript as the list draws it: [mappedMessages] with date separators and the unread
     * divider. The boundary and config are inputs rather than reads of the current state, so a
     * page never takes its separators from one config and its neighbour's from another.
     *
     * Nothing is emitted while the boundary is still resolving. The list decides where to open from
     * the first page it lays out, and a page drawn before the boundary landed has no divider to
     * open at. Both wait on the same chat id, and the resolution is two local queries.
     */
    val messages: Flow<PagingData<ChatListItem>> =
        combine(
            mappedMessages,
            stateFlow.map { it.unreadBoundary }
                .filterNot { it is UnreadBoundary.Resolving }
                .distinctUntilChanged(),
            stateFlow.map { it.separatorConfig }.distinctUntilChanged(),
            oldestEncryptedId,
        ) { paging, boundary, config, oldestEncrypted ->
            paging.withSeparators(boundary, config, oldestEncrypted)
        }

    /**
     * The citation shown for [this] message.
     *
     * The accent comes from the message's own sender id, not from `State.participant`:
     * [ChatParticipant.Contact] wraps a device contact and carries no user id, so the participant
     * is not a usable source for a counterparty's colour.
     */
    /**
     * Who the citation says said it.
     *
     * A DM's counterparty is the chat's, so the participant answers for every message they sent.
     * A group has no counterparty and a different name per message, so the answer is the cited
     * sender's own profile — the same map the transcript draws their bubble from. Missing means
     * the device has never seen them, which a request fixes for the next emission; the strip
     * showing a blank name is what it looked like before.
     */
    private suspend fun ChatMessage.quoteAuthorName(): String {
        if (isFromSelf) return resources.getString(R.string.title_you)

        val profiles = senderProfiles.value ?: return stateFlow.value.participant?.name.orEmpty()
        val senderId = senderId ?: return ""
        val profile = profiles[senderId.hexEncodedString()]
        if (profile == null || profile.displayName.isBlank()) {
            chatCoordinator.requestSenderProfile(senderId)
        }

        return when (profile) {
            // Still resolving: a placeholder name here would flash before the real one lands.
            null -> ""
            else -> nameOrHandle(profile.displayName, profile.handle)
                ?: resources.getString(R.string.title_unnamedUser)
        }
    }

    private fun MessageContent.Media.toPhotoSnippet(message: ChatMessage) = ChatQuoteSnippet.Photo(
        caption = caption?.text?.takeIf { it.isNotBlank() },
        rendition = items.firstOrNull()?.renditions?.firstOrNull(),
        sealed = message.encryption != null,
        redacted = message.redacted,
        chatId = stateFlow.value.chatId,
        senderId = message.senderId,
    )

    private suspend fun ChatMessage.toQuote(): ChatQuote {
        val body = content.firstOrNull()
        val palette = senderId?.let { generateComplementaryColorPalette(it) }
        return ChatQuote(
            messageId = messageId,
            authorName = quoteAuthorName(),
            snippet = when (body) {
                is MessageContent.Cash -> ChatQuoteSnippet.Cash(
                    amount = body.amount,
                    tokenName = body.tokenName.ifBlank {
                        tokenCoordinator.getTokenMetadata(body.mint)
                            .getOrNull()?.token?.name.orEmpty()
                    },
                )

                is MessageContent.Text -> ChatQuoteSnippet.Text(body.text)

                is MessageContent.Media -> body.toPhotoSnippet(this)

                // Named the way the chat list previews it, so a citation and its source agree.
                is MessageContent.Widget -> ChatQuoteSnippet.Text(
                    when (body.widget) {
                        is WidgetContent.ShareProfile ->
                            resources.getString(R.string.label_chat_preview_sharedProfile)
                        WidgetContent.Unsupported -> ""
                    }
                )

                // A reply to a reply cites the inner body, not the nested citation.
                is MessageContent.Reply -> when (val inner = body.content.firstOrNull()) {
                    is MessageContent.Media -> inner.toPhotoSnippet(this)
                    else -> ChatQuoteSnippet.Text(
                        body.content.filterIsInstance<MessageContent.Text>()
                            .firstOrNull()?.text.orEmpty()
                    )
                }

                else -> ChatQuoteSnippet.Text("")
            },
            accent = palette?.first,
            nameAccent = palette?.second,
            senderIdHex = senderId?.hexEncodedString(),
        )
    }

    private val maxAmountFlow by lazy {
        combine(
            transactionController.limits,
            tokenCoordinator.observeSelectedTokenMint()
                .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
            exchange.observePreferredRate(),
        ) { limits, balance, rate ->
            val balanceInLocal = balance.convertingTo(rate)
            val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
            Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    // Every payment from the conversation is a plain send. The one that opens a tip DM is paid from
    // the person's profile, which is where the recipient's fee applies.
    private fun amountStyle() = AmountEntryStyle(
        // One label for both kinds of payment. The chat above the keypad already says who this is
        // going to and why; the slider only has to say what the gesture does.
        actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_swipeToSend)),
        actionStyle = ConfirmationStyle.Slide,
        infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
        overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
        standingHint = AmountEntryStyle.StandingHint.Ceiling,
    )

    private val amountStyleFlow by lazy { MutableStateFlow(amountStyle()) }

    val amountDelegate by lazy {
        AmountEntryDelegate(
            exchange = exchange,
            scope = viewModelScope,
            style = amountStyleFlow,
            loadingState = stateFlow.map { it.sendProgress }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
            maxAmount = maxAmountFlow,
            tokenChanges = tokenCoordinator.observeSelectedTokenMint(),
        )
    }

    /**
     * The last chance to write a draft. A backgrounded app can be killed without another callback
     * reaching this ViewModel, so STOPPED — not teardown — is what makes process death survivable.
     */
    private val draftFlushObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = flushDraft()
    }

    // Declared above init, whose photo handlers read them as soon as they start.
    /** Camera files by chip id, deleted once the photo is encoded into the app's own storage. */
    private val captureFiles = HashMap<String, File>()

    /** Shots staged at the shutter and not yet written, by file: the chip id and its readiness. */
    private val pendingCaptures = HashMap<Uri, Pair<String?, CompletableDeferred<Boolean>>>()

    /**
     * Loads and maps the transcript's first page as soon as the chat id is known, into the cache
     * the list reads from. The list subscribes only after the screen's first frame, which on a
     * busy device left the page waiting a few hundred milliseconds it could have spent loading.
     * Nothing reads from this presenter, so it never asks for more than the first page.
     */
    private fun initTranscriptPrefetch() {
        val presenter = object : PagingDataPresenter<ChatListItem.ContentBubble>(dispatchers.Default) {
            override suspend fun presentPagingDataEvent(
                event: PagingDataEvent<ChatListItem.ContentBubble>,
            ) = Unit
        }
        pagingScope.launch { mappedMessages.collectLatest { presenter.collectFrom(it) } }
    }

    init {
        // Essential — needed immediately for chat display
        initChatHandlers()
        initTranscriptPrefetch()
        initLinkCardFreshness()
        initClaimReplies()
        initMentionHandlers()
        initDraftHandlers()
        initGroupAnalytics()

        viewModelScope.launch {
            // Yield to let the first frame render before setting up remaining collectors
            initTokenAndExchangeObservers()
            initTypingHandlers()
            initSendHandlers()
            initPhotoHandlers()
            initMentionPicker()
            initMessageActionHandlers()
        }

        // Last, so the open handler is already collecting when the event goes out.
        savedStateHandle.get<ChatIdentifier>(ARG_CHAT)?.let(::openChat)
    }

    /**
     * Keeps a rendered cash link honest about its claim state, from the two directions a claim can
     * come from.
     *
     * **This device.** The reader taps a voucher, the link goes back out through the URL handler,
     * and the shell claims it over a bill drawn on top of this screen. The card is never told; it
     * is still composed, still showing the answer it was drawn from, and the answer has just
     * stopped being true. [CashLinkClaims] names the entropy, the resolver forgets it and bumps its
     * revision, and the card re-asks and tears in place. Every settled attempt, not only a
     * successful one, because "already claimed" and "expired" are the same news arriving as an
     * error.
     *
     * **Anyone else.** Nothing tells this device that a link it is drawing was collected, so the
     * sender watches their own voucher say "Tap to claim" for cash that is gone. There is no
     * signal to wait for, so the alternative is to ask — see [refreshLinkCards] for why that is
     * cheaper than it sounds, and [CashLinkClaims] for the signal that would retire it.
     *
     * The same claim is also the other side's news, which is why [thankForClaim] hangs off this
     * collector: a claim made here is the one thing this device knows that the sender does not, and
     * a reply on the transcript is how it tells them.
     */
    private fun initLinkCardFreshness() {
        // One collector for two consequences rather than one each. The flow is replay-less over a
        // single-slot buffer that drops rather than suspends, so a second collector is a second
        // chance to be the one that misses an emission.
        cashLinkClaims.settledClaims
            .onEach { claim ->
                linkCardResolver.invalidateCash(claim.entropy)
                thankForClaim(claim)
            }
            .launchIn(viewModelScope)

        // The voucher being claimed reads as claiming until the claim settles, rather than offering
        // the claim again for a re-tap to start a second one.
        cashLinkClaims.claimInFlight
            .onEach { linkCardResolver.markClaiming(it) }
            .launchIn(viewModelScope)

        // STARTED rather than a timer of its own, which makes one construct cover both cases worth
        // covering: the first pass runs on every foreground edge, so a reader who put the phone
        // down and came back gets a fresh answer immediately, and the loop then carries the case
        // they are actually in -- watching the chat while the other side collects. Backgrounding
        // ends it, so nothing is asked on behalf of a screen nobody is looking at.
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshLinkCards()
                    delay(CLAIM_REFRESH_INTERVAL)
                }
            }
        }
    }

    /**
     * Re-asks about any voucher still drawn as claimable.
     *
     * Not a poll in the expensive sense: [LinkCardResolver.refreshClaimable] drops nothing unless
     * an answer it is holding says `Claimable`, and no drop means no revision bump and so no
     * query. A chat with no cash link in view — nearly all of them — costs a walk of an empty map
     * per tick. A claimed or expired card is terminal and is never asked about again.
     */
    private suspend fun refreshLinkCards() {
        linkCardResolver.refreshClaimable()
    }

    /**
     * Records which voucher the reader just tapped, so the claim that comes back has a message to
     * answer. Recorded, not acted on — [ClaimReplyTargets] says why a tap is not yet a claim.
     */
    private fun initClaimReplies() {
        eventFlow.filterIsInstance<Event.CashLinkOpened>()
            .onEach { event ->
                // Not recorded for a viewer who cannot post: nothing would come of the thank-you
                // but a send the server refuses.
                val tap = stateFlow.value.cashCardTap
                if (tap is CashCardTap.Collect && tap.thanks) claimReplyTargets.tapped(event.entropy)
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CashLinkRefused>()
            .onEach { event ->
                val (title, message) = when (event.tap) {
                    CashCardTap.ChatToCollect ->
                        R.string.title_chatToCollect to R.string.description_chatToCollect
                    else -> R.string.title_joinToCollect to R.string.description_joinToCollect
                }
                BottomBarManager.showInfo(
                    title = resources.getString(title),
                    message = resources.getString(message),
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Opens where a tapped `@handle` leads. Looked up through [linkCardResolver], so a handle a
     * person card or an earlier tap already resolved opens without a round trip.
     *
     * An unclaimed handle and a failed lookup get different dialogs: one is an answer, the other
     * is worth trying again.
     */
    private fun initMentionHandlers() {
        eventFlow.filterIsInstance<Event.MentionTapped>()
            .onEach { event ->
                val lookup = linkCardResolver.lookUpUser(LinkCard.User.Identity.ByUsername(event.username))
                val counterpart = (stateFlow.value.participant as? ChatParticipant.TipUser)?.userId
                when (val destination = mentionDestination(event.username, lookup, counterpart)) {
                    is MentionDestination.NoSuchAccount -> BottomBarManager.showInfo(
                        title = resources.getString(R.string.error_title_usernameNotFound),
                        message = resources.getString(R.string.error_description_usernameNotFound, destination.username),
                    )
                    MentionDestination.LookupFailed -> BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_profileUnavailable),
                        message = resources.getString(R.string.error_description_profileUnavailable),
                    )
                    else -> dispatchEvent(Event.OpenMention(destination))
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Replies to the voucher with a thank-you, once a link tapped here has actually been collected.
     *
     * This is what closes the loop for everyone else in the chat without a server change. The
     * sender is watching a card they cannot be told about — nothing pushes a claim, and the poll in
     * [refreshLinkCards] is the fallback for exactly that — but a message arrives on a transcript
     * that already fans out to every participant on both platforms, and it stays there for whoever
     * opens the chat tomorrow. It reads as the claimer because it is: a plain text reply, sent the
     * way the composer sends one, attributed to them.
     *
     * Which claims qualify is [ClaimReplyTargets]'s to answer: it is what limits this to a link the
     * reader opened from this transcript and a claim that actually moved money.
     *
     * A message because a message is what there is. A reaction on the voucher is the smaller thing
     * to say, and is where this goes once the transcript can carry one; only the last line here
     * changes, because what decides to thank anyone does not.
     */
    private fun thankForClaim(claim: SettledClaim) {
        val messageId = claimReplyTargets.settled(claim) ?: return
        // After settled(), so the target is still consumed while the reply is off.
        if (!CLAIM_THANKS_ENABLED) return
        val chatId = stateFlow.value.chatId ?: return

        // Decided on the collector, sent off it. The claim is consumed before this returns, so the
        // next one is not waiting behind a network call on a single-slot flow that drops rather
        // than suspends -- and a card's freshness does not wait on a thank-you either.
        viewModelScope.launch {
            chatCoordinator.sendMessage(
                chatId,
                resources.getString(R.string.message_cash_link_thanks),
                messageId,
            ).onFailure { trace("claim reply failed to send - ${it.localizedMessage}") }
        }
    }

    /**
     * Gets the chat on screen onto it: its own row if the device has one, otherwise the server's
     * copy, and then the transcript if the viewer is entitled to it.
     *
     * A chat reached by invite link or push tap may never have been synced — the group feed, a join
     * and the roster stream all only ever carry chats the viewer is already in — so without the
     * hydration the screen has no title, no info card and no gate, and nothing that would later
     * supply them. A chat the device already holds costs no round trip: [ChatCoordinator.hydrateChat]
     * answers [ChatHydration.Stored] for it and the Room observation stays the only source, so
     * opening a synced chat behaves exactly as it did.
     *
     * A chat the server has no record of is left unloaded. A DM opened on its derived id before
     * anyone has written in it — from a scanned tip card or a profile — has no messages, and asking
     * for them comes back DENIED. The first tip loads the chat once it exists.
     *
     * The transcript is fetched only for a viewer the gate lets through. A withheld group is drawn
     * as a placeholder rather than fetched, so asking for its messages would pull the very ones the
     * blur is over — and an unknown membership is withheld here for the same reason it is on screen.
     * A member whose feed has not synced yet is not stranded: the group feed sync sends its own load
     * for a group whose cursor is still at zero. An eligible non-member's transcript is fetched once
     * the gate says so, off [State.readsFromOutside], rather than here, where the balance that
     * decides it has not arrived yet.
     */
    private suspend fun openTranscript(chatId: ChatId) {
        when (val hydration = chatCoordinator.hydrateChat(chatId)) {
            ChatHydration.Stored,
            ChatHydration.Unavailable -> chatCoordinator.loadMessages(chatId)
            ChatHydration.Absent -> Unit
            is ChatHydration.Fetched -> {
                val membership = hydration.membership
                if (membership.metadata.type == ChatType.GROUP) {
                    dispatchEvent(Event.OnGroupResolved(membership))
                } else {
                    chatCoordinator.loadMessages(chatId)
                }
            }
        }
    }

    private fun initChatHandlers() {
        // Ahead of the transcript, so the first mapping already has the real windows rather than
        // the fallbacks the default carries.
        userFlags.resolvedFlags
            .map {
                MessagePolicy.fromFlags(
                    editWindow = it.messageEditWindow.effectiveValue,
                    deleteWindow = it.messageDeleteWindow.effectiveValue,
                )
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.MessagePolicyChanged(it)) }
            .launchIn(viewModelScope)

        // Unified chat open handler — resolves chatId and contact from the identifier
        eventFlow
            .filterIsInstance<Event.OnChatOpened>()
            .onEach { event ->
                val identifier = event.identifier

                // 1. Resolve chatId
                val chatId = when (identifier) {
                    is ChatIdentifier.ByContact -> identifier.chatId
                        ?: chatCoordinator.getChatId(identifier.contact).getOrNull()
                    is ChatIdentifier.ByChatId -> identifier.chatId
                    is ChatIdentifier.ByUser -> error(ROUTED_TO_PROFILE)
                }

                // Re-entering the same, already-open chat (e.g. returning from the amount-entry
                // step) re-dispatches OnChatOpened. The chat is already resolved and its messages
                // are cached in Room, so skip the re-resolve + network reload that would invalidate
                // Paging and reflow the message list. Still keep the chat active and clear
                // notifications.
                if (chatId != null && openedChatId == chatId) {
                    chatCoordinator.setActiveChatId(chatId)
                    chatCoordinator.dismissNotifications(chatId)
                    return@onEach
                }
                openedChatId = chatId

                if (chatId != null) {
                    dispatchEvent(Event.ChatFound(chatId))
                    restoreDraft(chatId)
                    chatCoordinator.setActiveChatId(chatId)
                    // A featured group tapped on a profile is usually one the viewer is not in, so
                    // nothing is stored and the profile would sit empty through GetChat. The row
                    // that was tapped already carries the group's metadata: draw from it now, and
                    // let the fetch below replace it. A stored chat is left to Room, which knows
                    // the membership this placeholder cannot.
                    featuredGroups.peek(chatId)
                        ?.takeIf { chatCoordinator.observeMetadata(chatId).first() == null }
                        ?.let { dispatchEvent(Event.OnGroupResolved(ChatMembership(metadata = it, isMember = null))) }
                    viewModelScope.launch { openTranscript(chatId) }
                    // A feed row has no cover, so fetch it now rather than when the group's
                    // profile opens: the profile would otherwise draw an empty cover through GetChat.
                    viewModelScope.launch {
                        val metadata = chatCoordinator.observeMetadata(chatId).firstOrNull()?.metadata
                        if (metadata?.type == ChatType.GROUP && metadata.coverPicture == null) {
                            chatCoordinator.refreshCover(chatId)
                        }
                    }
                    chatCoordinator.dismissNotifications(chatId)
                } else {
                    // No existing chat means no messages yet, so typing stays disabled. The
                    // observeMessages-driven typing flow only fires once a chatId exists, so mark
                    // the typing state resolved here explicitly — otherwise the bottom bar would
                    // stay hidden forever for a brand-new contact.
                    dispatchEvent(Event.TypingEnabled(false))
                }

                // 2. Resolve contact
                when (identifier) {
                    is ChatIdentifier.ByContact -> {
                        val resolved = contactCoordinator.lookupContact(identifier.contact.e164).getOrNull()
                            ?: chatId?.let { contactCoordinator.lookupContactByDmChatId(it.toString()) }
                            ?: identifier.contact
                        dispatchEvent(Event.OnContactFound(resolved))
                    }
                    is ChatIdentifier.ByChatId -> {
                        val contact = contactCoordinator.lookupContactByDmChatId(
                            identifier.chatId.toString()
                        )
                        val isGroup = chatCoordinator.observeMetadata(identifier.chatId).first()
                            ?.metadata?.type == ChatType.GROUP
                        if (contact != null) {
                            dispatchEvent(Event.OnContactFound(contact))
                        } else if (!isGroup) {
                            // No device contact backs this chat — it's a tip DM. Mark it immediately
                            // (this lookup is local) so the send button renders condensed without
                            // waiting on the profile below, then warm the member store (fetch +
                            // persist if nothing is cached) so the reactive tip-identity collector
                            // can resolve the counterparty from their server profile. Identity is set
                            // reactively (see initChatHandlers), not here, so it can't be missed by a
                            // fast tap on "Send $".
                            dispatchEvent(Event.OnTipDmDetected)
                            viewModelScope.launch { chatCoordinator.getOtherMember(identifier.chatId) }
                        }
                    }
                    is ChatIdentifier.ByUser -> error(ROUTED_TO_PROFILE)
                }
            }
            .launchIn(viewModelScope)

        // Resolve owner authority for sending cash
        eventFlow
            .filterIsInstance<Event.OnContactFound>()
            .onEach { event ->
                viewModelScope.launch {
                    contactCoordinator.resolve(event.contact.e164)
                        .onSuccess { dispatchEvent(Event.ResolveCompleted) }
                        .onFailure { dispatchEvent(Event.ResolveFailed) }
                }
            }.launchIn(viewModelScope)

        // Resolve the tip counterparty reactively from the chat members. Tip DMs have no device
        // contact, so identity (name + avatar + user id) comes from the other member's server
        // profile — the same source the tips list uses. Reactive so it settles as soon as the
        // members are available and can't be missed by the send gate. Never clobbers a device
        // contact: the OnTipUserResolved reducer keeps an existing Contact participant.
        //
        // Skipped for a group, whose "other member" is only the first row of the roster. A roster
        // change re-emits the members and the group's metadata together, so resolving one here would
        // re-title the group as a DM with that member until OnGroupResolved lands again.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                combine(
                    chatCoordinator.observeMembers(chatId),
                    chatCoordinator.observeMetadata(chatId).map { it?.metadata?.type },
                ) { members, type -> members.takeUnless { type == ChatType.GROUP } }
            }
            .mapNotNull { members -> members?.firstOrNull { it.userId != userManager.accountId } }
            .distinctUntilChanged()
            .onEach { member -> dispatchEvent(Event.OnTipUserResolved(member.userId, member.userProfile)) }
            .launchIn(viewModelScope)

        // A group's identity is its own row, not a counterparty. Observed rather than read once
        // because every field the chrome shows moves under the user: a roster event changes the
        // member count, a join flips membership, a rules change moves the gate.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .filterNotNull()
            .filter { it.metadata.type == ChatType.GROUP }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnGroupResolved(it)) }
            .launchIn(viewModelScope)

        // The same metadata, minus the type filter: every chat has viewer state and every chat can
        // be muted, so this one is not the group chrome's business. Observed rather than read with
        // the chat because a mute made on another device arrives on the stream, and because leaving
        // clears it server-side.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .map { it?.metadata?.viewerState }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnViewerStateResolved(it)) }
            .launchIn(viewModelScope)

        // The profile footer's claim. Decided by the policy from the chat's metadata, so the
        // screen never reads the server's flag itself.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .map { it?.metadata?.let(e2eePolicy::shouldEncrypt) ?: false }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnEncryptionResolved(it)) }
            .launchIn(viewModelScope)

        // Observed rather than read once: the rule can change under an open screen, and a buy
        // hydrates a fresher copy of the token. See observeRuleToken for why it fetches as well.
        stateFlow.map { state ->
            (state.subject as? ChatSubject.Group)?.rules.balanceRequirement()
                ?.mints?.firstOrNull()?.let { Mint(it.bytes) }
        }
            .distinctUntilChanged()
            .flatMapLatest { mint -> mint?.let { tokenCoordinator.observeRuleToken(it) } ?: flowOf(null) }
            .map { token ->
                // The name rather than the symbol, matching the create form's own mint row: the two
                // screens name one holding, and the balance list a requirement is satisfied from
                // renders the name as well (`TokenWithBalance.displayName`). `brandedName` is the
                // same rule the link cards use, so the reserve reads "Dollars" here too rather than
                // "USDF".
                token?.let {
                    RuleCurrency(
                        name = it.brandedName(resources),
                        isReserve = it.isReserve,
                    )
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRuleCurrencyResolved(it)) }
            .launchIn(viewModelScope)

        // The profile names the token behind both the join and the chat row, which may differ.
        // Each is fetched like the gate's, and a mint that never resolves does not hold the others
        // back: it simply stays unnamed. The icon rides along for the profile's Token card.
        stateFlow.map { state ->
            GroupBalanceRequirements.from((state.subject as? ChatSubject.Group)?.rules)
                ?.let { listOfNotNull(it.join, it.chat) }.orEmpty()
                .mapNotNull { it.mints.firstOrNull()?.let { mint -> Mint(mint.bytes) } }
                .distinct()
        }
            .distinctUntilChanged()
            .flatMapLatest { mints ->
                if (mints.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        mints.map { mint ->
                            tokenCoordinator.observeRuleToken(mint)
                                .map<Token, Pair<List<Byte>, RuleCurrency>?> {
                                    mint.bytes to RuleCurrency(it.brandedName(resources), it.isReserve, it.imageUrl)
                                }
                                .onStart { emit(null) }
                        }
                    ) { named -> named.filterNotNull().toMap() }
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRuleTokensResolved(it)) }
            .launchIn(viewModelScope)

        // What the profile's pinned button does and says, re-decided as the balance and rates move.
        stateFlow.map { it.subject as? ChatSubject.Group }
            .distinctUntilChanged()
            .flatMapLatest { group ->
                if (group == null) {
                    flowOf(null)
                } else {
                    combine(
                        tokenCoordinator.tokenBalances,
                        userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
                        exchange.observeRates(),
                    ) { balances, staff, rates ->
                        GroupProfileStanding.of(
                            isMember = group.isMember == true,
                            rules = group.rules,
                            balances = balances,
                            isStaff = staff,
                            rates = rates,
                            viewerId = userManager.accountId,
                            creatorId = group.creator,
                        )
                    }
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnProfileStandingResolved(it)) }
            .launchIn(viewModelScope)

        // Sampled as soon as a public group resolves, so the profile has its grid on the way in
        // rather than popping it in after the push, and again each time the profile opens: this
        // view model outlives the screen, and who is chatting is a thing that moves. The list on
        // screen stays until the new one lands. A private group's sampling is denied, so it is
        // not asked; a failure reads as nobody to show.
        merge(
            stateFlow.map { (it.subject as? ChatSubject.Group)?.takeUnless { group -> group.isPrivate }?.chatId }
                .filterNotNull()
                .distinctUntilChanged(),
            eventFlow.filterIsInstance<Event.GroupProfileOpened>().mapNotNull { stateFlow.value.chatId },
        ).onEach { chatId ->
            val group = stateFlow.value.subject as? ChatSubject.Group ?: return@onEach
            if (group.isPrivate) {
                dispatchEvent(Event.OnChattersLoaded(emptyList()))
                return@onEach
            }
            val chatters = chatCoordinator.sampleChatters(chatId)
                .onFailure { trace("failed to sample chatters - ${it.localizedMessage}") }
                .getOrNull()?.chatters
            // A failed refresh keeps what is already showing; only a first failure hides the grid.
            if (chatters != null || stateFlow.value.chatters.isEmpty()) {
                dispatchEvent(Event.OnChattersLoaded(chatters.orEmpty()))
            }
        }.launchIn(viewModelScope)

        // A row that came from the feed can lack the cover the profile shows; GetChat has it. The
        // stored metadata updates and OnGroupResolved carries it into the subject.
        eventFlow.filterIsInstance<Event.GroupProfileOpened>()
            .onEach {
                val chatId = stateFlow.value.chatId ?: return@onEach
                val group = stateFlow.value.subject as? ChatSubject.Group ?: return@onEach
                if (group.coverPicture == null) chatCoordinator.refreshCover(chatId)
            }
            .launchIn(viewModelScope)

        // Re-resolved whenever membership or the rules move, and internally whenever the balance
        // does. flatMapLatest rather than combine because the balance flow is the inner one: a new
        // subject must cancel the gate it was deciding, not race it.
        stateFlow.map { it.subject as? ChatSubject.Group }
            .distinctUntilChanged()
            .flatMapLatest { group ->
                if (group == null) {
                    flowOf(null)
                } else {
                    tokenCoordinator.groupAccess(
                        isMember = group.isMember == true,
                        rules = group.rules,
                        isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
                        rates = exchange.observeRates(),
                    )
                }
            }
            .filterNotNull()
            .onEach { dispatchEvent(Event.OnGroupAccessResolved(it)) }
            .launchIn(viewModelScope)

        // The speaker rules, read from the chat's metadata for every chat type: the Flipcash welcome
        // chat is a DM that carries a `never` rule. A chat without rules, as most DMs are, speaks.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            // The creator rides along with the rules: a `creator` rule is met by comparing the viewer
            // to it, and the same metadata carries both.
            .map { it?.metadata?.let { metadata -> metadata.rules to metadata.creator } }
            .distinctUntilChanged()
            .flatMapLatest { rulesAndCreator ->
                val rules = rulesAndCreator?.first
                if (rules == null) {
                    flowOf(null)
                } else {
                    tokenCoordinator.speakerBlock(
                        rules = rules,
                        isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
                        rates = exchange.observeRates(),
                        viewerId = userManager.accountId,
                        creatorId = rulesAndCreator.second,
                    )
                }
            }
            .onEach { dispatchEvent(Event.OnSpeakerBlockResolved(it)) }
            .launchIn(viewModelScope)

        // Observe member identity — if the other member loses identity (e.g. unlinked
        // their phone), mark the chat as read-only. Gated by chat type through the same rule the
        // feed filters on, so a tip DM — addressed by user id, named by handle — is never
        // deactivated for lacking a name or a phone.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                combine(
                    chatCoordinator.observeMembers(chatId),
                    stateFlow.map { it.chatType }.distinctUntilChanged(),
                ) { members, chatType -> members to chatType }
            }
            .map { (members, chatType) ->
                val selfId = userManager.accountId
                val other = members.firstOrNull { it.userId != selfId }
                if (other != null) !isDmAddressable(chatType, other.userProfile) else false
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.ChatDeactivated(isReadOnly = it)) }
            .launchIn(viewModelScope)

        // An eligible non-member reads the group in full, but nothing else fetches its transcript:
        // openTranscript leaves a group the viewer is outside of unloaded, and the group feed only
        // carries groups the viewer is in. Keyed on the chat so a balance that dips and recovers
        // refetches rather than trusting a page that may have moved on.
        stateFlow.map { state -> state.chatId.takeIf { state.readsFromOutside } }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { chatId -> chatCoordinator.loadMessages(chatId) }
            .launchIn(viewModelScope)

        // Advance read pointer when user scrolls to messages
        eventFlow
            .filterIsInstance<Event.AdvanceReadPointer>()
            .onEach { event ->
                val state = stateFlow.value
                val chatId = state.chatId ?: return@onEach
                // A read pointer is a member's: an eligible non-member can see the messages now,
                // but the server refuses to move a pointer for someone outside the chat.
                if (state.isOutsideGroup) return@onEach
                viewModelScope.launch { chatCoordinator.advanceReadPointer(chatId, event.messageId) }
            }
            .launchIn(viewModelScope)
    }

    private fun initTokenAndExchangeObservers() {
        // Token observation
        tokenCoordinator.observeSelectedTokenMint()
            .flatMapLatest { mint ->
                tokenCoordinator.tokenBalances.map { tokens ->
                    tokens.find { it.token.address == mint }
                }
            }
            .filterNotNull()
            .onEach { tokenWithBalance ->
                dispatchEvent(Event.TokenUpdated(tokenWithBalance.token))
            }.launchIn(viewModelScope)

        exchange.observePreferredRate()
            .onEach { rate ->
                val currency = exchange.getCurrency(rate.currency.name)
                if (currency != null) {
                    amountDelegate.onCurrencyChanged(currency)
                    dispatchEvent(Event.OnCurrencySymbolUpdated(currency.symbol.ifEmpty { "$" }))
                }
            }.launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.LimitsChanged(it)) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initTypingHandlers() {
        // Dispatch typing notifications based on text changes.
        // transformLatest auto-cancels the previous block on each new emission,
        // replacing manual Job tracking for idle timeout and heartbeats.
        snapshotFlow { stateFlow.value.chatInputState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .transformLatest { text ->
                if (!stateFlow.value.typingConstraints.enabled) return@transformLatest

                if (text.isEmpty()) {
                    if (stateFlow.value.isSelfTyping) {
                        emit(Event.OnSelfTypingStopped)
                    }
                    return@transformLatest
                }

                if (!stateFlow.value.isSelfTyping) {
                    emit(Event.OnSelfTypingStarted)
                }

                val constraints = stateFlow.value.typingConstraints
                var elapsed = Duration.ZERO
                while (elapsed < constraints.timeout) {
                    val wait = minOf(constraints.interval, constraints.timeout - elapsed)
                    delay(wait)
                    elapsed += wait
                    if (elapsed < constraints.timeout) {
                        emit(Event.OnSelfTypingStill)
                    }
                }
                emit(Event.OnSelfTypingStopped)
            }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        // Send STOPPED_TYPING when keyboard is dismissed
        eventFlow.filterIsInstance<Event.OnStopMessageInput>()
            .onEach {
                if (stateFlow.value.isSelfTyping) {
                    dispatchEvent(Event.OnSelfTypingStopped)
                }
            }
            .launchIn(viewModelScope)

        // Notify server of typing state changes (fire-and-forget to avoid
        // blocking SharedFlow emission when the gRPC call hangs offline)
        eventFlow.filterIsInstance<Event.OnSelfTypingStarted>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STARTED_TYPING) } }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSelfTypingStill>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STILL_TYPING) } }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSelfTypingStopped>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STOPPED_TYPING) } }
            .launchIn(viewModelScope)

        // Observe typing indicators once chatId is known
        stateFlow.mapNotNull { it.chatId }
            .flatMapLatest { chatId -> chatCoordinator.observeTypingIndicators(chatId) }
            .onEach { typists -> dispatchEvent(Event.TypistsUpdated(typists)) }
            .launchIn(viewModelScope)

        // Faces for the typing indicator, from the same profiles the transcript attributes
        // messages with. A typist the roster doesn't cover is asked for, like an unknown sender,
        // and draws the fallback until their profile lands.
        combine(
            stateFlow.map { it.typists }.distinctUntilChanged(),
            stateFlow.map { it.chatType }.distinctUntilChanged(),
            senderProfiles,
            ::Triple,
        )
            .onEach { (typists, _, profiles) ->
                profiles ?: return@onEach
                typists.filter { it.userId.hexEncodedString() !in profiles }
                    .forEach { chatCoordinator.requestSenderProfile(it.userId) }
            }
            .map { (typists, chatType, profiles) ->
                typingAvatars(typists, chatType, profiles.orEmpty())
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.TypingAvatarsUpdated(it)) }
            .launchIn(viewModelScope)

        // A contact DM opens its composer once a payment has been exchanged. A tip DM has no such
        // wait: the payment that creates it is made from the profile, so the chat only exists
        // once it has been paid, and it opens ready to type. A group has no exchange to wait for
        // either: its own rules say who may post, and [GroupAccess] has already applied them — a
        // group that reaches the composer at all is [GroupAccess.Membered], and one that has not
        // is showing the gate bar instead.
        combine(
            stateFlow.mapNotNull { it.chatId }.distinctUntilChanged(),
            stateFlow.map { it.subject is ChatSubject.Group || it.chatType == ChatType.TIP_DM }
                .distinctUntilChanged(),
            ::Pair,
        )
            .flatMapLatest { (chatId, alwaysOpen) ->
                if (alwaysOpen) {
                    flowOf(true)
                } else {
                    chatCoordinator.observeMessages(chatId)
                        .map { messages ->
                            messages.any { msg -> msg.content.any { it is MessageContent.Cash } }
                        }
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.TypingEnabled(it)) }
            .launchIn(viewModelScope)
    }

    /**
     * The group events that are not an RPC's answer: the gate appearing, and the taps on the
     * invite sheet, an invite card, the gate's funding button and the group profile. The screens report the taps
     * as events so the member count and the gate mint come from the state this holds.
     */
    private fun initGroupAnalytics() {
        // Once per view model, which is once per visit: the chat flow scopes it to the Chat route.
        // Keyed on the gate being drawn — outside the group with the access decided — and not on
        // every re-decision after, so a balance moving under an open chat does not send it again.
        stateFlow
            .mapNotNull { state ->
                val group = state.subject as? ChatSubject.Group ?: return@mapNotNull null
                if (!state.isOutsideGroup) return@mapNotNull null
                val access = when (state.groupAccess) {
                    GroupAccess.Eligible -> AnalyticsGroupAccess.ELIGIBLE
                    is GroupAccess.Blocked -> AnalyticsGroupAccess.BLOCKED
                    // Undetermined is a guess waiting on a rate; the view is logged once it settles.
                    GroupAccess.Membered, GroupAccess.Undetermined, null -> return@mapNotNull null
                }
                GroupEvents.gateShown(access, group.rules.gateMint, group.memberCount.toInt())
            }
            .take(1)
            .onEach { analytics.track(it) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.InviteSheetOpened>()
            .onEach { event ->
                val group = stateFlow.value.subject as? ChatSubject.Group ?: return@onEach
                analytics.track(GroupEvents.inviteSheetOpened(event.source, group.memberCount.toInt()))
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.InviteLinkShared>()
            .onEach { analytics.track(GroupEvents.inviteShared(GroupInviteMethod.SHARE)) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.GateFundingTapped>()
            .onEach { event ->
                val group = stateFlow.value.subject as? ChatSubject.Group
                analytics.track(GroupEvents.gateFundingTapped(event.method, group?.rules.gateMint))
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.InviteCardFollowed>()
            .onEach { analytics.track(GroupEvents.inviteFollowed(GroupInviteSource.CHAT_CARD)) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.GroupInfoOpened>()
            .onEach {
                val group = stateFlow.value.subject as? ChatSubject.Group ?: return@onEach
                analytics.track(
                    GroupEvents.infoOpened(group.memberCount.toInt(), isMember = group.isMember == true)
                )
            }
            .launchIn(viewModelScope)
    }

    private suspend fun loadQuickReactionInputs(): QuickReactionInputs {
        val catalog = emojiCatalogLoader.load()
        val fill = catalog.firstCategoryEntries.map { it.emoji }
        val undrawable = withContext(dispatchers.Default) {
            fill.filterNot { EmojiDrawability.isDrawable(it) }.toSet()
        }
        return QuickReactionInputs(
            recentStats = recentReactionsStore.stats(),
            catalogFill = fill,
            undrawable = undrawable,
        )
    }

    private fun initMessageActionHandlers() {
        viewModelScope.launch { dispatchEvent(Event.QuickReactionInputsLoaded(loadQuickReactionInputs())) }

        eventFlow.filterIsInstance<Event.ToggleReaction>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                viewModelScope.launch {
                    chatCoordinator.toggleReaction(chatId, event.messageId, event.emoji)
                    // A toggle is what moves recents, so the next strip reflects it.
                    dispatchEvent(Event.QuickReactionInputsLoaded(loadQuickReactionInputs()))
                }
            }
            .launchIn(viewModelScope)

        // The selection holds the bubble as it was when pressed; a reaction that lands while it is
        // up (the viewer's own from another device, or a toggle's reconcile) reaches the strip here.
        // Reads the live overlay, the same source [mappedMessages] treats as authoritative.
        stateFlow.map { it.selection?.takeIf { bubble -> bubble.canReact }?.messageId }
            .distinctUntilChanged()
            .flatMapLatest { messageId ->
                if (messageId == null) {
                    emptyFlow()
                } else {
                    reactionOverlay.mapNotNull { overlay -> overlay[messageId]?.selfReactions }
                        .distinctUntilChanged()
                        .map { Event.SelectionReactionsChanged(messageId, it) }
                }
            }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        // See ReactionRefreshPlanner and MessageList's reaction-refresh effects: the transcript
        // computes which ids need a refresh (the newest window on open/resume, Room-sourced pages
        // as they page in) and hands the batch down here — this just forwards it to the
        // coordinator. No reducer case: nothing about visible state changes from this.
        eventFlow.filterIsInstance<Event.RefreshReactionIds>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                if (event.messageIds.isEmpty()) return@onEach
                // Launched: offline the RPC doesn't return, and waiting on it here holds up every
                // event after it on the shared bus.
                viewModelScope.launch { chatCoordinator.refreshReactions(chatId, event.messageIds) }
            }
            .launchIn(viewModelScope)

        // A failed add/remove the coordinator has already rolled back optimistically — this only
        // tells the reader why the pill snapped back. One line, as iOS's toast is.
        chatCoordinator.reactionErrors
            .onEach { error ->
                toastController.showToast(
                    when (error) {
                        ReactionError.REACTION_FAILED -> R.string.title_reactionNotAdded
                        ReactionError.TOO_MANY_REACTION_TYPES -> R.string.title_reactionLimitReached
                    },
                    replacePrevious = true,
                )
            }
            .launchIn(viewModelScope)

        // Read off the state rather than carried on the event, so the row that copies the link and
        // the row that shares it are handing out the one url [State.shareableGroupInviteUrl] builds.
        eventFlow.filterIsInstance<Event.CopyInviteLink>()
            .mapNotNull { stateFlow.value.shareableGroupInviteUrl }
            .onEach { url ->
                clipboardManager.setText(
                    text = url,
                    label = resources.getString(R.string.title_clipboardLabelGroupInviteLink),
                )
                analytics.track(GroupEvents.inviteShared(GroupInviteMethod.COPY))
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CopyMessage>()
            .onEach { event ->
                clipboardManager.setText(
                    text = event.text,
                    label = resources.getString(R.string.title_clipboardLabelMessage),
                )
            }
            .launchIn(viewModelScope)

        // Pre-filling the composer writes to the live TextFieldState, so it runs on the main
        // thread for the same reason clearing it after a send does.
        eventFlow.filterIsInstance<Event.EditMessage>()
            .onEach { event -> stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(event.text) }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.SubmitEdit>()
            .onEach {
                val editing = stateFlow.value.editing ?: return@onEach
                val chatId = stateFlow.value.chatId ?: return@onEach
                val text = stateFlow.value.chatInputState.text.toString()
                finishEditing(editing)

                // Confirming an unchanged edit is still a way out of edit mode; it just isn't a
                // request. An empty body isn't an edit either — deleting is the other action.
                if (text.isBlank() || text == editing.originalText) return@onEach

                viewModelScope.launch {
                    chatCoordinator.editMessage(chatId, editing.messageId, text)
                        .onFailure { cause ->
                            trace("failed to edit message - ${cause.localizedMessage}")
                            BottomBarManager.showError(
                                title = resources.getString(R.string.title_messageNotEdited),
                                message = resources.getString(R.string.description_messageNotEdited),
                            )
                        }
                }
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CancelEdit>()
            .onEach { finishEditing(stateFlow.value.editing ?: return@onEach) }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.DeleteMessage>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.title_deleteMessage),
                    message = resources.getString(R.string.description_deleteMessage),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_deleteForEveryone),
                        ) {
                            viewModelScope.launch {
                                chatCoordinator.deleteMessage(chatId, event.messageId)
                                    .onFailure { cause ->
                                        trace("failed to delete message - ${cause.localizedMessage}")
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.title_messageNotDeleted),
                                            message = resources.getString(R.string.description_messageNotDeleted),
                                        )
                                    }
                            }
                        },
                    ),
                    showCancel = true,
                    // Closing the sheet ends the selection either way. Cancelling would otherwise
                    // leave the message alone behind the backdrop with a bar the user just backed
                    // out of, which reads as a second confirmation still pending.
                    onDismiss = { dispatchEvent(Event.ClearMessageSelection) },
                )
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.JoinChat>()
            .onEach {
                val chatId = stateFlow.value.chatId ?: run {
                    dispatchEvent(Event.JoinStateUpdated())
                    return@onEach
                }
                // Read before the call: the roster the count comes from moves with the join.
                val group = stateFlow.value.subject as? ChatSubject.Group
                // No optimistic flip. `join` caches the chat and the membership flag comes back
                // through observeMetadata, which is the same path a join from another device takes —
                // one source for the gate rather than two that can disagree.
                chatCoordinator.join(chatId)
                    .also { analytics.trackJoined(group, it) }
                    .onSuccess {
                        // The gate holds its own confirmation rather than waiting to be replaced:
                        // membership comes back through the roster, which can land on the next frame
                        // and swap the checkmark away before it has been drawn. Clearing the success
                        // after the hold is what releases the gate, so the blur fades and the
                        // composer arrives when the button is done rather than when the roster is.
                        dispatchSuccessThen(Event.JoinStateUpdated(success = true)) {
                            dispatchEvent(Event.JoinStateUpdated())
                        }
                    }
                    .onFailure { cause ->
                        trace("failed to join chat - ${cause.localizedMessage}")
                        // A refusal is the only thing that tells the user anything: the gate itself
                        // does not change, because the rules and the balance it reads are what the
                        // server just disagreed with. Saying which refusal it was is the difference
                        // between "buy more" and "this link is dead".
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToJoin),
                            message = resources.getString(
                                when (cause) {
                                    is JoinChatError.RulesNotSatisfied ->
                                        R.string.error_description_joinChat_rulesNotSatisfied
                                    is JoinChatError.Denied ->
                                        R.string.error_description_joinChat_denied
                                    is JoinChatError.NotFound ->
                                        R.string.error_description_joinChat_notFound
                                    else -> R.string.error_description_failedToJoin
                                }
                            ),
                        )
                        dispatchEvent(Event.JoinStateUpdated())
                    }
            }
            .launchIn(viewModelScope)


        eventFlow.filterIsInstance<Event.LeaveChat>()
            .mapNotNull { stateFlow.value.subject as? ChatSubject.Group }
            .onEach { group ->
                BottomBarManager.showInfo(
                    title = resources.getString(
                        R.string.prompt_title_leaveChat,
                        group.title,
                    ),
                    message = resources.getString(R.string.prompt_description_leaveChat),
                    actions = listOf(
                        BottomBarAction(text = resources.getString(R.string.action_leaveChat)) {
                            dispatchEvent(Event.LeaveConfirmed)
                        }
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.LeaveConfirmed>()
            .onEach {
                val chatId = stateFlow.value.chatId ?: return@onEach
                val memberCount = (stateFlow.value.subject as? ChatSubject.Group)?.memberCount?.toInt() ?: 0
                // `leave` clears the membership locally before the call, so the gate is back in
                // place by the time the profile closes — the same single source the join reads.
                chatCoordinator.leave(chatId)
                    .also { analytics.trackLeft(memberCount, it) }
                    .onSuccess { dispatchEvent(Event.LeftChat) }
                    .onFailure {
                        trace("failed to leave chat - ${it.localizedMessage}")
                        dispatchEvent(Event.LeaveFailed)
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToLeave),
                            message = resources.getString(R.string.error_description_failedToLeave),
                        )
                    }
            }
            .launchIn(viewModelScope)

        // A bubble is what the UI has; a citation is what the composer needs, and building one
        // reads the stored message. A message this device never stored drops the request rather
        // than opening a strip with nothing in it.
        eventFlow.filterIsInstance<Event.ReplyRequested>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val stored = chatCoordinator.getMessage(chatId, event.bubble.messageId)
                    ?: return@onEach
                dispatchEvent(Event.ReplyToMessage(stored.toQuote()))
            }
            .launchIn(viewModelScope)

        // A distance the device cannot measure is a message it never stored, and no walk would
        // reach it. Resolving here rather than in the list keeps the read off the composition and
        // gives the walk a bound before it starts.
        eventFlow.filterIsInstance<Event.JumpToMessage>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val distance = chatCoordinator.distanceFromNewest(chatId, event.messageId)
                    ?: return@onEach
                dispatchEvent(Event.JumpResolved(event.messageId, distance))
            }
            .launchIn(viewModelScope)

        // Read once per chat id. The list holds read reporting until this lands, so the pointer
        // it reads is the one the viewer arrived with.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .onEach { chatId ->
                val boundary = chatCoordinator.resolveUnreadBoundary(chatId)
                val budget = (boundary as? UnreadBoundary.At)
                    ?.let { chatCoordinator.countMessagesAfter(chatId, it.readThrough) }
                    ?: 0
                dispatchEvent(Event.UnreadBoundaryResolved(boundary, budget))
            }
            // The list waits on this, and on main it queued behind the transcript's first frame.
            .flowOn(dispatchers.IO)
            .launchIn(viewModelScope)
    }

    /** Leaves edit mode, restoring the draft the edit interrupted. */
    private fun finishEditing(editing: EditingMessage) {
        stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(editing.stashedDraft)
        dispatchEvent(Event.EditingEnded)
    }

    /**
     * Drives the mention picker from the composer's text and cursor.
     *
     * Each change works out the `@` word at the cursor and searches the roster for it; a new word
     * cancels the search still running for the last one. No word (whitespace typed, the cursor
     * moved off it, the `@` deleted, the text sent) closes the picker. Groups only, and only while
     * the viewer has a composer to type in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initMentionPicker() {
        // Once per visit, the first time the picker opens: that starts the composing session, which
        // fetches the group's mention pool.
        var refreshed = false
        // Bumped when that refresh lands, so the open word searches again and new joiners show
        // without another keystroke.
        val refreshes = MutableStateFlow(0)
        val eligible = stateFlow
            .map { it.chatType == ChatType.GROUP && !it.isAnonymous && !it.replacesComposer }
            .distinctUntilChanged()
        val word = snapshotFlow {
            val input = stateFlow.value.chatInputState
            activeMentionToken(input.text, input.selection)?.text
        }
        combine(eligible, word, refreshes) { canMention, query, refresh ->
            query.takeIf { canMention } to refresh
        }
            .distinctUntilChanged()
            .mapLatest { (query, _) ->
                val chatId = stateFlow.value.chatId
                if (query == null || chatId == null) return@mapLatest emptyList()
                if (!refreshed) {
                    refreshed = true
                    viewModelScope.launch {
                        runCatching { rosterSearch.refresh(chatId) }
                            .onSuccess { refreshes.update { it + 1 } }
                    }
                }
                rosterSearch.search(chatId, query).mentionable()
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnMentionSuggestions(it)) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.PickMention>()
            .onEach { event ->
                val username = event.match.username?.takeIf { it.isNotBlank() } ?: return@onEach
                val input = stateFlow.value.chatInputState
                val token = activeMentionToken(input.text, input.selection) ?: return@onEach
                val (text, cursor) = insertMention(input.text.toString(), token, username)
                input.edit {
                    replace(0, length, text)
                    selection = TextRange(cursor)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun initPhotoHandlers() {
        eventFlow.filterIsInstance<Event.StagePhotos>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                if (stateFlow.value.editing != null) return@onEach
                val room = MAX_STAGED_PHOTOS - stateFlow.value.stagedPhotos.size
                event.uris.take(room.coerceAtLeast(0)).forEach { uri ->
                    val id = mediaUploads.stage(chatId, uri)
                    if (event.captured) uri.path?.let { captureFiles[id] = File(it) }
                    dispatchEvent(Event.PhotoStaged(StagedPhoto(id, uri)))
                }
                // Anything over the limit is not staged, and a camera file for it has no use.
                if (event.captured) event.uris.drop(room.coerceAtLeast(0)).forEach { it.path?.let { path -> File(path).delete() } }
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CaptureStarted>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId
                val ready = CompletableDeferred<Boolean>()
                val canStage = chatId != null && stateFlow.value.editing == null &&
                    stateFlow.value.stagedPhotos.size < MAX_STAGED_PHOTOS
                if (!canStage) {
                    pendingCaptures[event.uri] = null to ready
                    return@onEach
                }
                val id = mediaUploads.stage(chatId!!, event.uri, ready)
                event.uri.path?.let { captureFiles[id] = File(it) }
                pendingCaptures[event.uri] = id to ready
                dispatchEvent(Event.PhotoStaged(StagedPhoto(id, event.uri, event.preview)))
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CaptureFinished>()
            .onEach { event ->
                val (id, ready) = pendingCaptures.remove(event.uri) ?: return@onEach
                ready.complete(event.saved)
                val staged = id != null && stateFlow.value.stagedPhotos.any { it.id == id }
                when {
                    // Not staged (no room), or removed while it was being written: nothing uses it.
                    !staged -> event.uri.path?.let { File(it).delete() }
                    !event.saved -> dispatchEvent(Event.RemovePhoto(id!!))
                }
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.RemovePhoto>()
            .onEach { event ->
                mediaUploads.remove(event.id)
                captureFiles.remove(event.id)?.delete()
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.RetryPhoto>()
            .onEach { event -> mediaUploads.retry(event.id) }
            .launchIn(viewModelScope)

        mediaUploads.allStates
            .onEach { states ->
                dispatchEvent(Event.UploadStatesChanged(states))
                // Past encoding the app holds its own copy, so the camera's can go.
                captureFiles.entries.removeAll { (id, file) ->
                    val encoded = when (states[id]) {
                        is ChatMediaUploadState.Uploading,
                        is ChatMediaUploadState.Processing,
                        is ChatMediaUploadState.Uploaded -> true
                        else -> false
                    }
                    if (encoded) file.delete()
                    encoded
                }
            }
            .launchIn(viewModelScope)
    }

    private fun initSendHandlers() {
        // Send text message
        eventFlow.filterIsInstance<Event.SendMessage>()
            .onEach {
                val textToSend = stateFlow.value.chatInputState.text.toString()
                val chatId = stateFlow.value.chatId ?: return@onEach
                val photos = stateFlow.value.composerPhotos
                if (!stateFlow.value.canSendComposer(hasText = textToSend.isNotBlank())) return@onEach
                val chatType = stateFlow.value.chatType
                // Read here, not in the reducer: the reply strip comes down with the draft, and
                // both are the composer emptying itself once the message is on its way.
                val replyToMessageId = stateFlow.value.replyingTo?.messageId

                stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd("")
                if (replyToMessageId != null) dispatchEvent(Event.CancelReply)
                // The chips leave the composer now; the send owns them from here, and they come
                // back below if it is refused.
                if (photos.isNotEmpty()) dispatchEvent(Event.PhotosSent(photos.map { it.id }.toSet()))
                // Now, not on delivery: the text is in the pending message from here on, and a
                // send that fails leaves a "Not sent" bubble with a retry, which is the
                // transcript's own record of it. Restoring a draft as well would duplicate it.
                chatDraftStore.saveInBackground(chatId, ChatDraftSnapshot.Empty)

                viewModelScope.launch {
                    val sent = if (photos.isEmpty()) {
                        chatCoordinator.sendMessage(chatId, textToSend, replyToMessageId)
                    } else {
                        chatCoordinator.sendMedia(chatId, photos.map { it.id }, textToSend.trim(), replyToMessageId)
                            .onFailure { dispatchEvent(Event.PhotosRestored(photos)) }
                    }
                    sent
                        .onSuccess {
                            trace("message sent successfully")
                            analytics.track(ChatEvents.sentMessage(chatType.analytics, null))
                        }
                        .onFailure { cause ->
                            trace("message failed to send - ${cause.localizedMessage}")
                            analytics.track(ChatEvents.sentMessage(chatType.analytics, cause.analytics))
                        }
                }
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        // Retry a failed message
        eventFlow.filterIsInstance<Event.RetryMessage>()
            .onEach { (pendingClientIdHex, content) ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val pendingId = pendingClientIdHex ?: return@onEach

                BottomBarManager.showInfo(
                    title = resources.getString(R.string.title_messageNotSent),
                    message = resources.getString(R.string.description_messageNotSent),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_retry),
                        ) {
                            viewModelScope.launch {
                                if (content.photoBody() != null) {
                                    chatCoordinator.retryMedia(chatId, pendingId)
                                        .onSuccess { trace("retry photo sent successfully") }
                                        .onFailure { trace("retry photo failed - ${it.localizedMessage}") }
                                } else {
                                    chatCoordinator.retryMessage(chatId, pendingId, listOf(content))
                                        .onSuccess { trace("retry message sent successfully") }
                                        .onFailure { trace("retry message failed - ${it.localizedMessage}") }
                                }
                            }
                        },
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        // confirmation of amount and checks
        eventFlow.filterIsInstance<Event.OnConfirmRequested>()
            .onEach { onConfirmRequested() }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSendCash>()
            // Contact DMs and tip DMs send to whichever participant backs the chat; a group has no
            // participant and sends a cash link instead. The final send branches on that type (see
            // Event.OnSendRequested).
            .filter { stateFlow.value.participant != null || stateFlow.value.chatType == ChatType.GROUP }
            .onEach {
                if (!startChattingPayer.mayProceed(
                        onAddMoney = { dispatchEvent(Event.PresentDepositOptions) },
                        onDiscoverCurrencies = { dispatchEvent(Event.OpenScreen(AppRoute.Token.Discovery, asSheet = true)) },
                    )
                ) {
                    return@onEach
                }
                amountDelegate.reset()
                dispatchEvent(Event.NavigateToAmountEntry)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                startChattingPayer.depositRoute()?.let { route ->
                    dispatchEvent(Event.OpenScreen(route))
                }
            }.launchIn(viewModelScope)

        // Send cash. The transfer itself is delegated by chat type: a contact DM pays a phone
        // number (contact metadata), a tip DM pays a user id (tip metadata). Both delegates resolve
        // the recipient, transfer, debit the local balance, and sync the feed. A group funds a cash
        // link and posts it (ChatCashLinks). This handler owns the shared amount verification, send
        // state, analytics, and error UI.
        eventFlow.filterIsInstance<Event.OnSendRequested>()
            .onEach { (amount, token) ->
                viewModelScope.launch {
                    val owner = userManager.accountCluster ?: return@launch
                    val rate = exchange.preferredRate

                    dispatchEvent(Event.SendStateUpdated(loading = true))

                    val source = owner.withTimelockForToken(token)

                    val balance = tokenCoordinator.balanceForToken(token)

                    val verifiedFiat = verifiedFiatCalculator.compute(
                        amount = amount,
                        token = token,
                        balance = balance,
                        rate = rate,
                    ).getOrElse { error ->
                        dispatchEvent(Event.SendStateUpdated())
                        val (title, message) = when (error) {
                            is ComputeVerifiedFiatError.AmountBelowMinimum -> {
                                R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                            }
                            else -> {
                                R.string.error_title_staleRates to R.string.error_description_staleRates
                            }
                        }
                        BottomBarManager.showAlert(
                            title = resources.getString(title),
                            message = resources.getString(message),
                        )
                        return@launch
                    }

                    val chatId = stateFlow.value.chatId

                    val result = when (val participant = stateFlow.value.participant) {
                        is ChatParticipant.Contact -> contactPaymentDelegate.send(
                            contact = participant.contact,
                            chatId = chatId,
                            verifiedFiat = verifiedFiat,
                            token = token,
                            source = source,
                        )
                        is ChatParticipant.TipUser -> tipPaymentDelegate.send(
                            userId = participant.userId,
                            verifiedFiat = verifiedFiat,
                            token = token,
                            source = source,
                            origin = TipOrigin.CHAT,
                            action = TipAction.SEND,
                        )
                        // A group has no one participant to pay, so the cash goes in as a link
                        // that the first member to tap claims.
                        null -> {
                            if (chatId == null || stateFlow.value.chatType != ChatType.GROUP) {
                                dispatchEvent(Event.SendStateUpdated())
                                return@launch
                            }
                            chatCashLinks.sendToChat(
                                chatId = chatId,
                                amount = verifiedFiat,
                                token = token,
                                owner = owner,
                            )
                        }
                    }

                    // Report what was sent. A group's send is a cash link; every other send from
                    // this screen is a plain cash send. The tip that opens a DM is paid from the
                    // profile, not here.
                    val isCashLink = stateFlow.value.participant == null
                    val sent = { state: AnalyticsState, error: String? ->
                        val sentAmount = verifiedFiat.localFiat.analytics
                        when {
                            isCashLink -> TransferEvents.sendCashLink(state, sentAmount, CashLinkChoice.GROUP_CHAT, null, error)
                            else -> TransferEvents.sentCash(state, sentAmount, error)
                        }
                    }

                    result.onSuccess {
                        dispatchEvent(Event.SendStateUpdated(success = true))
                        delay(400.milliseconds)
                        analytics.track(sent(AnalyticsState.SUCCESS, null))
                        dispatchEvent(
                            Dispatchers.Main,
                            Event.SendComplete(verifiedFiat.localFiat.nativeAmount)
                        )
                    }.onFailure { cause ->
                        dispatchEvent(Event.SendStateUpdated())
                        analytics.track(sent(AnalyticsState.FAILURE, cause.analytics))
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_cashFailedToSend),
                            message = resources.getString(R.string.error_description_cashFailedToSend),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    /**
     * What the composer would leave behind if this screen went away right now.
     *
     * An in-progress edit contributes the new-message draft it displaced rather than the edit
     * body, so a chat left mid-edit reopens in new-message mode with the user's own words — the
     * same place cancelling the edit would have put them.
     */
    private fun draftSnapshot(): ChatDraftSnapshot {
        val state = stateFlow.value
        return chatDraftOf(
            composerText = state.chatInputState.text.toString(),
            replyTarget = state.replyingTo?.toDraftReply(),
            editStash = state.editing?.stashedDraft,
        )
    }

    /** Writes off [viewModelScope], which is already cancelled by the time [onCleared] runs. */
    private fun flushDraft() {
        val chatId = stateFlow.value.chatId ?: return
        chatDraftStore.saveInBackground(chatId, draftSnapshot())
    }

    @OptIn(FlowPreview::class)
    private fun initDraftHandlers() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(draftFlushObserver)

        // The composer text lives in a TextFieldState, so it never re-emits through stateFlow and
        // needs its own snapshot subscription; the reply strip and the edit stash do come from
        // state. Debounced to keep a database write off the keystroke path — the flushes are what
        // cover the cases a debounce would lose.
        combine(
            snapshotFlow { stateFlow.value.chatInputState.text.toString() },
            stateFlow.map { it.replyingTo }.distinctUntilChanged(),
            stateFlow.map { it.editing }.distinctUntilChanged(),
        ) { _, _, _ -> }
            .drop(1) // the empty composer the screen opens with, before a restore can have run
            .debounce(DRAFT_WRITE_DEBOUNCE)
            .onEach {
                val chatId = stateFlow.value.chatId ?: return@onEach
                chatDraftStore.save(chatId, draftSnapshot())
            }
            .launchIn(viewModelScope)
    }

    /**
     * Puts back what was left in this chat. Silent by design: the text and the reply strip return,
     * focus and the keyboard do not.
     */
    private suspend fun restoreDraft(chatId: ChatId) {
        val draft = chatDraftStore.load(chatId) ?: return
        // Pre-filling the composer writes to the live TextFieldState, so it runs on the main thread.
        withContext(Dispatchers.Main.immediate) {
            stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(draft.text)
        }
        val reply = draft.replyTarget ?: return
        // The stored copy keeps a photo's words only. When the cited message is already on this
        // device its quote is rebuilt from it, so the strip gets the thumbnail back.
        val quote = runCatching { chatCoordinator.getMessage(chatId, reply.messageId) }.getOrNull()
            ?.takeIf { it.content.firstOrNull() is MessageContent.Media }
            ?.toQuote()
            ?: reply.toChatQuote()
        dispatchEvent(Event.ReplyToMessage(quote))
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(draftFlushObserver)
        flushDraft()
        // This chat's claim only, not whatever is active now: opening a chat directly from
        // another one disposes this entry after the incoming one has claimed the chat, so an
        // unconditional clear would silence the chat the user just opened.
        chatCoordinator.clearActiveChat(stateFlow.value.chatId)
        linkCardResolver.dispose()
        // Photos staged but never sent are not worth keeping upload state for. Sent ones have left
        // this list already and belong to the send.
        stateFlow.value.stagedPhotos.forEach {
            mediaUploads.remove(it.id)
            captureFiles.remove(it.id)?.delete()
        }
    }

    private fun checkBalanceLimit(amount: Fiat): Boolean {
        val token = stateFlow.value.token ?: return false
        val rate = exchange.preferredRate
        val balance = tokenCoordinator.balanceForToken(token)
        val balanceInLocal = balance.convertingTo(rate)
        val isOverBalance = amount.valueGreaterThan(balanceInLocal)
        if (isOverBalance) {
            startChattingPayer.presentInsufficientBalance(
                onAddMoney = { dispatchEvent(Event.PresentDepositOptions) },
            )
        }
        return isOverBalance
    }

    private fun checkSendLimit(amount: Double): Boolean {
        val currency = amountDelegate.state.value.currency
        val sendLimit =
            currency.code?.let { stateFlow.value.limits?.sendLimitFor(it) } ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached),
            )
        }
        return isOverLimit
    }

    /**
     * @param fixedAmount the amount when the flow priced the payment rather than the user typing
     * it — the fee that opens a tip DM. Null reads the keypad entry.
     */
    private fun onConfirmRequested(fixedAmount: Fiat? = null) {
        val enteredAmount = fixedAmount?.toDouble() ?: amountDelegate.state.value.enteredAmount
        val amount = fixedAmount ?: Fiat(enteredAmount, exchange.preferredRate.currency)

        if (checkBalanceLimit(amount) || checkSendLimit(enteredAmount)) return

        if (enteredAmount <= 0) return

        val token = stateFlow.value.token ?: return

        if (stateFlow.value.resolveState is ResolveState.Resolved) {
            dispatchEvent(Event.OnSendRequested(
                amount = amount,
                token = token,
            ))
        }
    }

    companion object {
        /** The [ChatIdentifier] this view model opens on creation, in its [SavedStateHandle]. */
        const val ARG_CHAT = "chat"

        /**
         * How often a visible claimable voucher is re-asked about.
         *
         * Picked against what the reader is doing rather than against load: they sent cash in a
         * chat and are waiting to see it collected, so a card that stays stale for the better part
         * of a minute reads as broken. Only a transcript actually showing an unclaimed voucher
         * queries at all — see [refreshLinkCards].
         */
        private val CLAIM_REFRESH_INTERVAL = 15.seconds

        /**
         * Whether a collected claim is answered with [thankForClaim]'s reply. Off for now; the
         * tap and claim bookkeeping still runs, so turning this back on is the only change needed.
         */
        private const val CLAIM_THANKS_ENABLED = false

        private const val ROUTED_TO_PROFILE =
            "ChatIdentifier.ByUser is redirected to the profile before it reaches a ChatViewModel"

        /** Long enough to coalesce a burst of typing, short enough to survive a fast exit. */
        private val DRAFT_WRITE_DEBOUNCE = 300.milliseconds

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            stateReducer(event, UNREAD_DIVIDER_LIFETIME)
        }

        /** [updateStateForEvent] under an explicit [lifetime], so tests can cover both policies. */
        internal fun stateReducer(event: Event, lifetime: UnreadDividerLifetime): (State) -> State =
            when (event) {
                is Event.OnChatOpened -> { state ->
                    when (val id = event.identifier) {
                        is ChatIdentifier.ByContact ->
                            state.copy(
                                subject = ChatSubject.Contact(ChatParticipant.Contact(id.contact)),
                                chatType = ChatType.CONTACT_DM,
                            )
                        // Known without a lookup, so the transcript, boundary and the rest keyed
                        // on it start now rather than once the open handler gets to ChatFound.
                        is ChatIdentifier.ByChatId -> state.copy(chatId = id.chatId)
                        is ChatIdentifier.ByUser -> error(ROUTED_TO_PROFILE)
                    }
                }
                is Event.OnContactFound -> { state ->
                    state.copy(
                        subject = ChatSubject.Contact(ChatParticipant.Contact(event.contact)),
                        chatType = ChatType.CONTACT_DM,
                    )
                }
                // "No device contact" is also true of every group, so a group that has already
                // resolved keeps its type.
                Event.OnTipDmDetected -> { state ->
                    if (state.subject is ChatSubject.Group) state
                    else state.copy(chatType = ChatType.TIP_DM)
                }
                is Event.OnGroupResolved -> { state ->
                    val metadata = event.membership.metadata
                    val previous = state.subject as? ChatSubject.Group
                    // The access was decided for the old membership and rules. Kept across a change
                    // to either, a stale Eligible would unblur a group whose rules just tightened
                    // until the balance flow caught up, so it goes back to unknown instead.
                    val accessStale = previous == null ||
                        previous.isMember != event.membership.isMember ||
                        previous.rules != metadata.rules
                    state.copy(
                        groupAccess = if (accessStale) null else state.groupAccess,
                        subject = ChatSubject.Group(
                            chatId = metadata.chatId,
                            groupTitle = metadata.title,
                            picture = metadata.picture,
                            memberCount = metadata.rosterSummary.memberCount,
                            rules = metadata.rules,
                            isMember = event.membership.isMember,
                            creator = metadata.creator,
                            description = metadata.description,
                            coverPicture = metadata.coverPicture,
                            isPrivate = metadata.isPrivate,
                        ),
                        chatType = ChatType.GROUP,
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnRuleCurrencyResolved ->
                    { state -> state.copy(ruleCurrency = event.currency) }
                is Event.OnGroupAccessResolved -> { state -> state.copy(groupAccess = event.access) }
                is Event.OnSpeakerBlockResolved -> { state -> state.copy(
                    speakerBlock = event.block?.requirement,
                    speakerBlocksReactions = event.block?.reactionsBlocked == true,
                    speakerBlockResolved = true,
                ) }
                Event.JoinChat -> { state ->
                    state.copy(joinProgress = LoadingSuccessState(loading = true))
                }
                is Event.JoinStateUpdated -> { state ->
                    state.copy(
                        joinProgress = LoadingSuccessState(
                            event.loading,
                            event.success,
                        )
                    )
                }
                is Event.OnEncryptionResolved -> { state -> state.copy(isEncrypted = event.isEncrypted) }

                is Event.OnViewerStateResolved -> { state ->
                    state.copy(viewerState = event.viewerState)
                }
                Event.CopyInviteLink,
                is Event.InviteSheetOpened,
                Event.InviteLinkShared,
                is Event.GateFundingTapped,
                Event.GroupInfoOpened,
                Event.InviteCardFollowed,
                Event.LeaveChat, Event.GroupProfileOpened -> { state -> state }
                is Event.OnChattersLoaded -> { state -> state.copy(chatters = event.chatters) }
                is Event.OnProfileStandingResolved -> { state -> state.copy(profileStanding = event.standing) }
                is Event.OnRuleTokensResolved -> { state -> state.copy(ruleTokens = event.tokens) }
                Event.LeaveConfirmed -> { state -> state.copy(leaving = true) }
                Event.LeftChat, Event.LeaveFailed -> { state -> state.copy(leaving = false) }
                is Event.OnTipUserResolved -> { state ->
                    // A device contact, once matched, wins over the server profile (it carries the
                    // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
                    // profile identity and mark the recipient resolved so the send can proceed (the
                    // tip user is known to exist; the tip send resolves their address at send time).
                    // A group has no counterparty: the member this names is just someone in the room.
                    if (state.subject is ChatSubject.Contact || state.subject is ChatSubject.Group) state
                    else state.copy(
                        subject = ChatSubject.TipUser(
                            ChatParticipant.TipUser(event.userId, event.profile)
                        ),
                        chatType = ChatType.TIP_DM,
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
                is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
                Event.OnSendCash -> { state -> state }
                Event.OnStartMessageInput -> { state -> state.copy(messageInputRequested = true) }
                Event.OnStopMessageInput -> { state -> state }
                Event.OnMessageInputConsumed -> { state -> state.copy(messageInputRequested = false) }
                is Event.TypistsUpdated -> { state -> state.copy(typists = event.typists) }
                is Event.TypingAvatarsUpdated -> { state -> state.copy(typingAvatars = event.avatars) }
                Event.ResolveCompleted -> { state ->
                    state.copy(resolveState = ResolveState.Resolved)
                }
                is Event.ResolveFailed -> { state ->
                    state.copy(resolveState = ResolveState.Failed)
                }
                is Event.SendMessage -> { state ->
                    val clears = lifetime == UnreadDividerLifetime.UntilSend &&
                        state.unreadBoundary is UnreadBoundary.At
                    if (clears) state.copy(unreadBoundary = UnreadBoundary.None) else state
                }
                is Event.RetryMessage -> { state -> state }
                is Event.StagePhotos -> { state -> state }
                is Event.CaptureStarted -> { state -> state }
                is Event.CaptureFinished -> { state -> state }
                is Event.RetryPhoto -> { state -> state }
                is Event.PhotoStaged -> { state ->
                    if (state.stagedPhotos.size >= MAX_STAGED_PHOTOS) state
                    else state.copy(stagedPhotos = state.stagedPhotos + event.photo)
                }
                is Event.RemovePhoto -> { state ->
                    state.copy(
                        stagedPhotos = state.stagedPhotos.filterNot { it.id == event.id },
                        uploadStates = state.uploadStates - event.id,
                    )
                }
                is Event.UploadStatesChanged -> { state -> state.copy(uploadStates = event.states) }
                is Event.PhotosSent -> { state ->
                    state.copy(stagedPhotos = state.stagedPhotos.filterNot { it.id in event.ids })
                }
                is Event.PhotosRestored -> { state ->
                    state.copy(stagedPhotos = (event.photos + state.stagedPhotos).take(MAX_STAGED_PHOTOS))
                }
                Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                is Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
                is Event.MentionTapped -> { state -> state }
                is Event.OpenMention -> { state -> state }
                is Event.OnConfirmRequested -> { state -> state }
                is Event.OnSendRequested -> { state -> state }
                is Event.SendStateUpdated -> { state ->
                    state.copy(
                        sendProgress = LoadingSuccessState(
                            event.loading,
                            event.success,
                        )
                    )
                }
                is Event.SendComplete -> { state -> state }
                Event.OnSelfTypingStarted -> { state -> state.copy(isSelfTyping = true) }
                Event.OnSelfTypingStill -> { state -> state }
                Event.OnSelfTypingStopped -> { state -> state.copy(isSelfTyping = false) }
                is Event.TypingEnabled -> { state ->
                    state.copy(
                        typingConstraints = state.typingConstraints.copy(
                            enabled = event.enabled,
                            resolved = true,
                        )
                    )
                }
                is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
                is Event.LimitsChanged -> { state -> state.copy(limits = event.limits) }
                is Event.AdvanceReadPointer -> { state -> state }
                is Event.ChatDeactivated -> { state -> state.copy(isAnonymous = event.isReadOnly) }
                is Event.MessagePolicyChanged -> { state -> state.copy(messagePolicy = event.policy) }
                is Event.ToggleMessageSelection -> { state ->
                    // By message rather than by row: a message split around its card is one
                    // selection whichever of its rows was pressed.
                    val alreadySelected = state.selection?.messageKey == event.bubble.messageKey
                    // Narrowed again so the bar offers what is open now, not when the row was built.
                    val selected = event.bubble.takeUnless { alreadySelected }?.narrowedFor(state)
                    state.copy(
                        selection = selected,
                        reactionStripOnly = false,
                        confirmingDelete = false,
                    ).withQuickReactionStrip()
                }
                is Event.PresentReactionStrip -> { state ->
                    val selected = event.bubble.narrowedFor(state).takeIf { it.canReact }
                    if (selected == null) {
                        state
                    } else {
                        state.copy(
                            selection = selected,
                            reactionStripOnly = true,
                            confirmingDelete = false,
                        ).withQuickReactionStrip()
                    }
                }
                Event.ClearMessageSelection -> { state ->
                    state.copy(selection = null, confirmingDelete = false).withQuickReactionStrip()
                }
                is Event.CopyMessage -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                Event.ReportRequested -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.EditMessage -> { state ->
                    state.copy(
                        selection = null,
                        confirmingDelete = false,
                        replyingTo = null,
                        editing = EditingMessage(
                            messageId = event.messageId,
                            originalText = event.text,
                            // Starting a second edit before the first ends must not stash the
                            // first edit's text as if it were the user's draft.
                            stashedDraft = state.editing?.stashedDraft
                                ?: state.chatInputState.text.toString(),
                        ),
                    )
                }
                // Selection survives the confirmation sheet, but the focus does not: the sheet is
                // modal, so the transcript behind it goes uniformly dim until the sheet closes.
                is Event.DeleteMessage -> { state -> state.copy(confirmingDelete = true) }
                Event.SubmitEdit -> { state -> state }
                Event.CancelEdit -> { state -> state }
                Event.EditingEnded -> { state -> state.copy(editing = null) }
                // The strip opens on ReplyToMessage, once the citation resolves; all this does is
                // take the selection bar down so the transcript is legible while that read runs.
                is Event.ReplyRequested -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.ReplyToMessage -> { state ->
                    state.copy(
                        replyingTo = event.quote,
                        selection = null,
                        confirmingDelete = false,
                        // Reply and edit both own the composer, so opening one closes the other.
                        editing = null,
                    )
                }
                Event.CancelReply -> { state -> state.copy(replyingTo = null) }
                is Event.OnMentionSuggestions -> { state -> state.copy(mentionSuggestions = event.matches) }
                // Closed at once rather than waiting for the inserted space to reach the query
                // observer, so the list doesn't linger a frame over the text it just wrote.
                is Event.PickMention -> { state -> state.copy(mentionSuggestions = emptyList()) }
                // The toggle itself is handled in initMessageActionHandlers (it calls the
                // coordinator); the reducer's only job is to take the bubble out of selection mode
                // when the tap came from the quick strip.
                is Event.ToggleReaction -> { state ->
                    if (event.clearsSelection) state.copy(selection = null) else state
                }
                is Event.QuickReactionInputsLoaded -> { state ->
                    state.copy(quickReactionInputs = event.inputs).withQuickReactionStrip()
                }
                is Event.SelectionReactionsChanged -> { state ->
                    val selection = state.selection
                    if (selection?.messageId != event.messageId) {
                        state
                    } else {
                        state.copy(selection = selection.copy(selfReactions = event.selfReactions))
                            .withQuickReactionStrip()
                    }
                }
                is Event.RefreshReactionIds -> { state -> state }
                // Nothing on screen moves when a voucher is tapped -- the link leaves, the card
                // keeps saying what it said, and the claim comes back as its own signal.
                is Event.CashLinkOpened -> { state -> state }
                is Event.CashLinkRefused -> { state -> state }
                // The request itself changes nothing: the target is only worth holding once the
                // walk's bound resolves, and that read is what decides whether it can be reached.
                is Event.JumpToMessage -> { state -> state }
                is Event.JumpResolved -> { state ->
                    state.copy(jumpTarget = event.messageId, jumpBudget = event.budget)
                }
                Event.JumpConsumed -> { state -> state.copy(jumpTarget = null, jumpBudget = null) }
                is Event.UnreadBoundaryResolved -> { state ->
                    state.copy(unreadBoundary = event.boundary, unreadWalkBudget = event.walkBudget)
                }
            }
    }
}

/**
 * This bubble with its capabilities narrowed to what is open now. The transcript resolved it when it
 * was mapped, which may have been well inside a window that has since closed, so a selection offers
 * what is open at the moment it is made. The same goes for membership: a viewer who has left since
 * keeps only what a reader outside the group may do.
 */
private fun ChatListItem.ContentBubble.narrowedFor(state: ChatViewModel.State): ChatListItem.ContentBubble {
    val open = capabilities.withinWindows(timestamp, state.messagePolicy)
    return copy(capabilities = if (state.isOutsideGroup) open.readOnly() else open)
}

/**
 * [ChatViewModel.State.quickReactionStrip] rebuilt for the current selection: empty while nothing
 * is selected, while the selected message can't be reacted to, or before the inputs have loaded.
 */
private fun ChatViewModel.State.withQuickReactionStrip(): ChatViewModel.State {
    val bubble = selection?.takeIf { it.canReact }
    val inputs = quickReactionInputs
    val strip = if (bubble == null || inputs == null) {
        emptyList()
    } else {
        ReactionStripComposer.compose(
            recentStats = inputs.recentStats,
            selfReactions = bubble.selfReactions,
            catalogFillSource = inputs.catalogFill,
            undrawable = inputs.undrawable,
        )
    }
    return copy(quickReactionStrip = strip)
}
