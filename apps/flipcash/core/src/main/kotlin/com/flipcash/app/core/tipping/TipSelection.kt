package com.flipcash.app.core.tipping

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.chat.ChatIdentifier
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.flipcash.app.core.AppRoute
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A chosen tip amount, tagged by how it was chosen: picked from a [Preset] or entered as a
 * [Custom] value. Both carry the underlying [value].
 */
sealed interface TipAmount {
    val value: Fiat

    /** An amount picked from the tip card's suggested presets. */
    data class Preset(override val value: Fiat) : TipAmount

    /** An amount entered via the custom-amount sheet. */
    data class Custom(override val value: Fiat) : TipAmount
}

/**
 * The current tip selection: the [amount] the sender has chosen, the [token] they'll tip with, and
 * the [sendState] of an in-flight tip submission.
 */
data class TipSelectionState(
    /** user ID for the scanned and presented tip card */
    val userId: ID? = null,
    /** The chosen tip amount (preset or custom), or `null` when nothing is selected. */
    val amount: TipAmount? = null,
    /** The token to tip with — mirrors the app-global selected token; `null` until resolved. */
    val token: Token? = null,
    /** Processing state of the tip submission (idle / loading / success). */
    val sendState: LoadingSuccessState = LoadingSuccessState(),
    /**
     * Whether the viewer can afford at least the minimum tip. The tip card is always
     * shown; when false the scanner hides the tip modal and prompts to add money.
     * Defaults to false so the modal stays hidden until affordability is confirmed.
     */
    val canTip: Boolean = false,
    /** Suggested tip presets in the sender's preferred currency; updates when the region changes. */
    val presets: List<Fiat> = emptyList(),
) {
    /** A positive amount is chosen and no submission is currently in flight. */
    val canConfirm: Boolean get() = amount != null && sendState.isIdle
}

/**
 * Selection state for the tip modal.
 *
 * Backed by the tipping coordinator (a singleton) and surfaced through [LocalTipCoordinator] so the
 * tip modal, the custom-amount sheet, and the token-selection sheet all read and write the same
 * [selection]. This lets a sheet opened over the still-visible tip card mutate the selection and
 * have the modal reflect it immediately, without the scanner feature depending on the tipping module.
 */
interface TipSelectionHolder {
    /** The combined tip selection (amount + token + send state). */
    val selection: StateFlow<TipSelectionState>

    /** Selects [amount] (a [TipAmount.Preset] or [TipAmount.Custom]); pass `null` to clear it. */
    fun selectAmount(amount: TipAmount?)

    /** Submits the current tip selection, driving [TipSelectionState.sendState] loading → success. */
    fun confirmTip()

    /**
     * One-shot UI events the tip decorator should act on — e.g. opening the deposit flow
     * after the user picks "Add Money" from an insufficient-balance prompt. Emitted by the
     * coordinator and collected where the navigator lives.
     */
    val events: Flow<TipEvent>

    /** No-op holder used as the [LocalTipCoordinator] default so consumers never see null. */
    object Empty : TipSelectionHolder {
        override val selection: StateFlow<TipSelectionState> = MutableStateFlow(TipSelectionState())
        override fun selectAmount(amount: TipAmount?) = Unit
        override fun confirmTip() = Unit
        override val events: Flow<TipEvent> = emptyFlow()
    }
}

/** One-shot events emitted by a [TipSelectionHolder] for the tip UI to handle. */
sealed interface TipEvent {
    /** Open [route] (e.g. the deposit flow) using the tip UI's navigator. */
    data class OpenRoute(val route: AppRoute) : TipEvent

    /**
     * Launch the DM chat for a completed tip. The tip UI opens it through the tips flow so the
     * tips list sits beneath the chat in the back stack (back returns to the list).
     */
    data class LaunchChat(val identifier: ChatIdentifier) : TipEvent
}

/** Provides the active [TipSelectionHolder] (the tipping coordinator) to composables. */
val LocalTipCoordinator = staticCompositionLocalOf<TipSelectionHolder> { TipSelectionHolder.Empty }
