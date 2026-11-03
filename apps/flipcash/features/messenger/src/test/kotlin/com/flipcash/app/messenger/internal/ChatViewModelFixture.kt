package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.messenger.internal.link.ComposerLinkPreview
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardMemory
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.chat.WrongKeyChats
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.RosterSearchSource
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.payments.group.GroupSendPaymentDelegate
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher

/**
 * The one place a test builds a [ChatViewModel]. Every dependency defaults to a relaxed mock (or a
 * real, empty instance where the type is a plain value), so a test passes only the collaborators it
 * stubs or asserts on. Adding a constructor parameter means one edit here, not one per test.
 *
 * [dispatcher] is the one required argument: it is the test's `MainCoroutineRule` dispatcher, which
 * the view model must share for its coroutines to run on the test's clock.
 */
internal fun chatViewModel(
    dispatcher: TestDispatcher,
    identifier: ChatIdentifier? = null,
    chatCoordinator: ChatCoordinator = mockk(relaxed = true),
    wrongKeyChats: WrongKeyChats = WrongKeyChats(),
    mediaUploads: ChatMediaUploads = noMediaUploads(),
    e2eePolicy: E2eePolicy = E2eePolicy(),
    contactCoordinator: ContactCoordinator = mockk(relaxed = true),
    contactPaymentDelegate: ContactPaymentDelegate = mockk(relaxed = true),
    tipPaymentDelegate: TipPaymentDelegate = mockk(relaxed = true),
    transactionController: TransactionController = mockk(relaxed = true),
    tokenCoordinator: TokenCoordinator = mockk(relaxed = true),
    exchange: Exchange = mockk(relaxed = true),
    verifiedFiatCalculator: VerifiedFiatCalculator = mockk(relaxed = true),
    startChattingPayer: StartChattingPayer = mockk(relaxed = true),
    userManager: UserManager = mockk(relaxed = true),
    resources: ResourceHelper = mockk(relaxed = true),
    analytics: FlipcashAnalytics = RecordingAnalytics(),
    clipboardManager: ClipboardManager = mockk(relaxed = true),
    userFlags: UserFlagsCoordinator = mockk(relaxed = true),
    linkCardClassifier: LinkCardClassifier = mockk(relaxed = true),
    linkCardResolver: LinkCardResolver = mockk(relaxed = true),
    cashLinkClaims: CashLinkClaims = mockk(relaxed = true) { every { claimInFlight } returns MutableStateFlow(null) },
    chatCashLinks: ChatCashLinks = mockk(relaxed = true),
    groupSendPayments: GroupSendPaymentDelegate = mockk(relaxed = true),
    chatDraftStore: ChatDraftStore = mockk(relaxed = true),
    recentReactionsStore: RecentReactionsStore = mockk(relaxed = true),
    emojiCatalogLoader: EmojiCatalogLoader = mockk(relaxed = true),
    toastController: SystemToastController = mockk(relaxed = true),
    userProfileDataSource: UserProfileDataSource = mockk(relaxed = true),
    rosterSearch: RosterSearchSource = mockk(relaxed = true),
    featuredGroups: FeaturedGroupsStore = FeaturedGroupsStore(mockk(relaxed = true)),
    lobbyRequests: LobbyRequests = mockk(relaxed = true),
    hostLobbies: HostLobbies = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
    groupRosters: GroupRosters = mockk(relaxed = true) { every { state } returns MutableStateFlow(emptyMap()) },
    linkCardMemory: LinkCardMemory? = null,
    composerLinkPreview: ComposerLinkPreview? = null,
): ChatViewModel = ChatViewModel(
    identifier = identifier,
    chatCoordinator = chatCoordinator,
    wrongKeyChats = wrongKeyChats,
    mediaUploads = mediaUploads,
    e2eePolicy = e2eePolicy,
    contactCoordinator = contactCoordinator,
    contactPaymentDelegate = contactPaymentDelegate,
    tipPaymentDelegate = tipPaymentDelegate,
    transactionController = transactionController,
    tokenCoordinator = tokenCoordinator,
    exchange = exchange,
    verifiedFiatCalculator = verifiedFiatCalculator,
    startChattingPayer = startChattingPayer,
    userManager = userManager,
    resources = resources,
    analytics = analytics,
    clipboardManager = clipboardManager,
    userFlags = userFlags,
    linkCardClassifier = linkCardClassifier,
    linkCardResolver = linkCardResolver,
    cashLinkClaims = cashLinkClaims,
    chatCashLinks = chatCashLinks,
    groupSendPayments = groupSendPayments,
    chatDraftStore = chatDraftStore,
    recentReactionsStore = recentReactionsStore,
    emojiCatalogLoader = emojiCatalogLoader,
    toastController = toastController,
    userProfileDataSource = userProfileDataSource,
    rosterSearch = rosterSearch,
    featuredGroups = featuredGroups,
    lobbyRequests = lobbyRequests,
    hostLobbies = hostLobbies,
    groupRosters = groupRosters,
    dispatchers = TestDispatcherProvider(dispatcher),
    linkCardMemory = linkCardMemory,
    composerLinkPreview = composerLinkPreview,
)
