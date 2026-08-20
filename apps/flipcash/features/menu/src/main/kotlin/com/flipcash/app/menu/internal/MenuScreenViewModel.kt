package com.flipcash.app.menu.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.bills.share.TipCodePreviewCache
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.app.core.share.TipCodeExporter
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.updates.ReleaseStage
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.menu.BuildConfig
import com.flipcash.features.menu.R
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.opencode.managers.MnemonicManager
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FullMenuList = buildList {
    add(MyAccount)
    add(AdvancedFeatures)
    add(SwitchAccount)
}

@HiltViewModel
internal class MenuScreenViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    authManager: AuthManager,
    versionInfo: VersionInfo,
    mnemonicManager: MnemonicManager,
    featureFlags: FeatureFlagController,
    private val toastController: SystemToastController,
    dispatchers: DispatcherProvider,
    releaseStageProvider: ReleaseStageProvider,
    purchaseMethodController: PurchaseMethodController,
    analytics: FlipcashAnalyticsService,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodePreviewCache: TipCodePreviewCache,
    private val shareable: ShareSheetController,
    private val clipboardManager: ClipboardManager,
    private val tipCodeExporter: TipCodeExporter,
    private val resources: ResourceHelper,
) :
    BaseViewModel<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent,
        defaultDispatcher = dispatchers.Default,
    ) {
    data class State(
        val items: List<MenuItem<Event>> = FullMenuList,
        val logoTapCount: Int = 0,
        val isStaff: Boolean = false,
        val flags: List<BetaFeature> = emptyList(),
        val unlockedBetaFeaturesManually: Boolean = false,
        val appVersionInfo: VersionInfo = VersionInfo(),
        val releaseTrack: String = "",
        // The viewer's own tip card, shown at the top of the v2 "You" tab. Null until resolved
        // (or when the profile has no display name).
        val tipCard: Scannable.TipCard? = null,
        // The shareable URL for [tipCard]. Displayed abbreviated; copied in full.
        val tipLink: String? = null,
    )

    sealed interface Event {
        data object OnVersionInfoClicked: Event
        data object CheckForUpdate: Event
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean): Event
        data class OnFeatureFlagsUpdated(val flags: List<BetaFeature>): Event
        data class OnAppVersionUpdated(val versionInfo: VersionInfo) : Event
        data class OnReleaseTrackDetermined(val stage: String): Event
        data class OnStaffUserDetermined(val staff: Boolean) : Event
        data object PresentDepositOptions: Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object OnSwitchAccountsClicked : Event
        data class OnSwitchAccountTo(val entropy: String): Event
        data class OnTipCardPopulated(val card: Scannable.TipCard, val link: String?) : Event
        data object ShareTipCard : Event
        data object CopyTipLink : Event
        data object DownloadTipCard : Event
        data class ExportTipCard(val format: TipCodeExportFormat) : Event
    }

    init {
        dispatchEvent(Event.OnAppVersionUpdated(versionInfo))
        dispatchEvent(Event.OnStaffUserDetermined(false))

        userManager.state
            .filter { it.authState is AuthState.Ready }
            .flatMapLatest { userFlags.resolvedFlags }
            .mapNotNull { it.isStaff.effectiveValue }
            .onEach {
                dispatchEvent(Event.OnStaffUserDetermined(it))
            }.launchIn(viewModelScope)

        featureFlags.observeOverride()
            .onEach { dispatchEvent(Event.OnBetaFeaturesUnlocked(it)) }
            .launchIn(viewModelScope)

        featureFlags.observe()
            .onEach { dispatchEvent(Event.OnFeatureFlagsUpdated(it)) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val resolvedStage = releaseStageProvider.resolvedStage
            val label = when {
                BuildConfig.DEBUG -> "development"
                resolvedStage == null || resolvedStage == ReleaseStage.Production -> null
                else -> resolvedStage.name.lowercase()
            }

            val formattedLabel = if (label != null) { " • $label" } else ""
            dispatchEvent(Event.OnReleaseTrackDetermined(formattedLabel))
        }

        eventFlow
            .filterIsInstance<Event.OnVersionInfoClicked>()
            .onEach {
                if (stateFlow.value.unlockedBetaFeaturesManually) {
                    if (stateFlow.value.logoTapCount - TAP_THRESHOLD > COUNTDOWN_START) {
                        toastController.showToast(R.string.toast_betaOverrideAlready, replacePrevious = true)
                    }
                    return@onEach
                }
                val remaining = TAP_THRESHOLD - stateFlow.value.logoTapCount + 1
                when {
                    remaining <= 0 -> {
                        featureFlags.enableBetaFeatures()
                        toastController.showToast(R.string.toast_betaOverrideEnabled, replacePrevious = true)
                    }
                    remaining <= COUNTDOWN_START -> {
                        toastController.showQuantityToast(R.plurals.toast_betaOverrideCountdown, remaining, remaining, replacePrevious = true)
                    }
                }
            }
            .launchIn(viewModelScope)

        @OptIn(FlowPreview::class)
        eventFlow
            .filterIsInstance<Event.OnVersionInfoClicked>()
            .debounce(500)
            .onEach { dispatchEvent(Event.CheckForUpdate) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSwitchAccountsClicked>()
            .map {
                authManager.selectAccount()
                    .fold(
                        onSuccess = {
                            authManager.logoutAndSwitchAccount(
                                mnemonicManager.getEncodedBase64(it)
                            )
                        },
                        onFailure = { Result.failure(it) }
                    )
            }.onResult(
                onError = { },
                onSuccess = { dispatchEvent(Event.OnSwitchAccountTo(it)) }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull {
                analytics.addMoneyOpened(Analytics.AddMoneySource.Menu)
                purchaseMethodController.presentDepositOptions(popToRoot = true)
            }.onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // Rebuild the viewer's own tip card whenever their profile becomes available/changes, so the
        // v2 "You" tab can show it at the top. Warm the Sharesheet preview eagerly so it's ready by
        // the time the user taps "Share as a Link".
        userManager.state
            .mapNotNull { it.userProfile }
            .distinctUntilChanged()
            .map { tippingCoordinator.resolveTipCard() }
            .onResult(onSuccess = { card ->
                val userId = tippingCoordinator.currentUserId
                dispatchEvent(Event.OnTipCardPopulated(card, userId?.let { Linkify.tipcard(it) }))
                userId?.let { tipCodePreviewCache.prepare(it, card) }
            })
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyTipLink>()
            .mapNotNull { stateFlow.value.tipLink }
            .onEach { link ->
                // The row shows an abbreviated link; the clipboard gets the whole thing.
                clipboardManager.setText(
                    text = link,
                    label = resources.getString(R.string.title_clipboardLabelTipCardLink),
                )
                toastController.showToast(R.string.action_copied, replacePrevious = true)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DownloadTipCard>()
            .onEach {
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.title_downloadTipCardAs),
                    actions = downloadOptions(resources) { format ->
                        dispatchEvent(Event.ExportTipCard(format))
                    },
                    showCancel = false,
                    showScrim = true,
                )
            }
            .launchIn(viewModelScope)

        // Render the chosen format, then hand the file to the Sharesheet — Android has no
        // permissionless "save to Photos", and the chooser already offers Files/Drive/Photos.
        eventFlow
            .filterIsInstance<Event.ExportTipCard>()
            .mapNotNull { event -> stateFlow.value.tipCard?.let { it to event.format } }
            .onEach { (card, format) ->
                val export = tipCodeExporter.export(card, format)
                if (export == null) {
                    BottomBarManager.showMessage(
                        title = resources.getString(R.string.error_title_tipCardExportFailed),
                        message = resources.getString(R.string.error_description_tipCardExportFailed),
                    )
                    return@onEach
                }
                shareable.present(
                    Shareable.TipCodeImage(
                        export = export,
                        title = resources.getString(R.string.title_shareTipCode),
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareTipCard>()
            .mapNotNull { tippingCoordinator.currentUserId }
            .map { userId ->
                // Title shown above the link, e.g. "Tip Ada" (same label as the card).
                val title = stateFlow.value.tipCard?.user?.displayName
                    ?.let { resources.getString(R.string.label_tipUser, it) }
                // Attach the eagerly-rendered preview if it's ready; null shares the URL alone.
                shareable.present(Shareable.TipCard(userId, tipCodePreviewCache.get(userId), title))
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        private const val TAP_THRESHOLD = 6
        private const val COUNTDOWN_START = 3

        private fun buildItemList(
            isStaff: Boolean,
            overrode: Boolean,
            flags: List<BetaFeature> = emptyList(),
        ): List<MenuItem<Event>> {
            return if (isStaff || overrode) {
                FullMenuList
                    .filter { item ->
                        val flagForItem = item.featureFlag
                        if (flagForItem != null) {
                            val match = flags.find { it.flag.key == flagForItem.key }
                            match?.enabled == true
                        } else {
                            true
                        }
                    }
            } else {
                FullMenuList.filterNot { it.isStaffOnly }
                    .filter { item ->
                        val flagForItem = item.featureFlag
                        if (flagForItem != null) {
                            val match = flags.find { it.flag.key == flagForItem.key }
                            match?.enabled == true
                        } else {
                            true
                        }
                    }
            }
        }

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnVersionInfoClicked -> { state ->
                    state.copy(logoTapCount = state.logoTapCount + 1)
                }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        unlockedBetaFeaturesManually = event.unlocked,
                        items = buildItemList(
                            isStaff = state.isStaff,
                            overrode = event.unlocked,
                            flags = state.flags
                        )
                    )
                }

                is Event.OnAppVersionUpdated -> { state ->
                    state.copy(appVersionInfo = event.versionInfo)
                }

                is Event.OnReleaseTrackDetermined -> { state ->
                    state.copy(releaseTrack = event.stage)
                }

                is Event.OnStaffUserDetermined -> { state ->
                    state.copy(
                        isStaff = event.staff,
                        items = buildItemList(
                            isStaff = event.staff,
                            overrode = state.unlockedBetaFeaturesManually,
                            flags = state.flags,
                        ),
                    )
                }

                is Event.OnTipCardPopulated -> { state ->
                    state.copy(tipCard = event.card, tipLink = event.link)
                }

                Event.PresentDepositOptions,
                Event.CheckForUpdate,
                Event.OnSwitchAccountsClicked,
                Event.ShareTipCard,
                Event.CopyTipLink,
                Event.DownloadTipCard,
                is Event.ExportTipCard,
                is Event.OpenScreen,
                is Event.OnSwitchAccountTo -> { state -> state }

                is Event.OnFeatureFlagsUpdated -> { state ->
                    state.copy(
                        items = buildItemList(
                            isStaff = state.isStaff,
                            overrode = state.unlockedBetaFeaturesManually,
                            flags = event.flags
                        ),
                        flags = event.flags,
                    )
                }
            }
        }
    }
}