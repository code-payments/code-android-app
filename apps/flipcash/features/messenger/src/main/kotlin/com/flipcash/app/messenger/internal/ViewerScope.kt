package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.isActiveAt
import com.flipcash.services.models.chat.silence
import com.flipcash.shared.chat.ChatCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What this chat holds about the viewer: mute, permissions and any moderator silence.
 *
 * The chat screen's first scope with its own slice, events, reducer and effects. It owns
 * [ViewerSlice] and [ViewerEvent]; [ChatViewModel] composes it in with `by` and routes every
 * [ViewerEvent] to [reduce]. Getters that read only this slice live on [ViewerSlice]; getters that
 * also read another scope's fields (`canSpeak`, `isReadOnlySpeaker`, `cashCardTap`) stay on
 * [ChatViewModel.State].
 *
 * Nothing on it is called from outside yet: every input arrives as a [ViewerEvent].
 */
internal interface ViewerScope

/** [ViewerScope]'s part of [ChatViewModel.State]. */
internal data class ViewerSlice(
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
    /**
     * The clock a timed silence is read against. A silence lapses with no event, so [silence]
     * compares the deadline to this and a timer bumps it at the deadline.
     */
    val silenceClock: Instant = Clock.System.now(),
) {
    /** The silence a moderator has on the viewer right now, or null once it has lapsed. */
    val silence: SilenceState?
        get() = viewerState.silence?.takeIf { it.isActiveAt(silenceClock) }

    /** `can_pin`: the creator of a group while a member, either member of a DM. Read, never derived from the type. */
    val canPin: Boolean
        get() = viewerState?.permissions?.canPin == true
}

/** The events [ViewerScope] reduces. All three are internal messages; none is a viewer action. */
internal sealed interface ViewerEvent : ChatViewModel.Event {
    /** This chat's viewer state moved, from the stream or from the viewer's own request. */
    data class OnViewerStateResolved(val viewerState: ViewerState?) : ViewerEvent

    /** A timed silence reached its deadline; nothing else says so. */
    data object SilenceLapsed : ViewerEvent

    /** The viewer can send again; the screen shows it as a floating toast above the composer. */
    data object SilenceLifted : ViewerEvent
}

/** [ViewerScope]'s reducer. Reads the wall clock, as the chat reducer did before the split. */
internal fun ViewerSlice.reduce(event: ViewerEvent): ViewerSlice = when (event) {
    is ViewerEvent.OnViewerStateResolved -> copy(viewerState = event.viewerState, silenceClock = Clock.System.now())
    ViewerEvent.SilenceLapsed -> copy(silenceClock = Clock.System.now())
    ViewerEvent.SilenceLifted -> this
}

/**
 * [ViewerScope]'s effects. [bind] takes the one field it reads from another scope, the chat id, as
 * its own flow, and can dispatch only [ViewerEvent]s, so it never writes another scope's state.
 */
internal class DefaultViewerScope @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
) : ViewerScope {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(
        scope: CoroutineScope,
        chatId: Flow<ChatId>,
        slice: Flow<ViewerSlice>,
        dispatch: (ViewerEvent) -> Unit,
    ) {
        // Every chat has viewer state and every chat can be muted, so this is not the group
        // chrome's business. Observed rather than read with the chat because a mute made on another
        // device arrives on the stream, and because leaving clears it server-side.
        chatId
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .map { it?.metadata?.viewerState }
            .distinctUntilChanged()
            .onEach { dispatch(ViewerEvent.OnViewerStateResolved(it)) }
            .launchIn(scope)

        // A timed silence lapses with nothing sent, so the composer comes back on a timer rather
        // than at the next stream event. The deadline is read off the viewer state, and a new
        // silence replaces the pending timer.
        slice.map { it.viewerState.silence as? SilenceState.Until }
            .distinctUntilChanged()
            .flatMapLatest { silence ->
                flow {
                    if (silence != null) {
                        val wait = silence.until - Clock.System.now()
                        if (wait.isPositive()) delay(wait)
                        emit(Unit)
                    }
                }
            }
            .onEach { dispatch(ViewerEvent.SilenceLapsed) }
            .launchIn(scope)

        // The pill giving way to the composer is the one moment worth a word. Read off the
        // silenced/not edge of the state, so a host's unsilence and a timer lapse that land together
        // are one lift, and a chat opened already free of silence has no edge to toast.
        slice.map { it.silence != null }
            .distinctUntilChanged()
            .scan(false to false) { (_, was), isSilenced -> was to isSilenced }
            .filter { (was, isSilenced) -> was && !isSilenced }
            .onEach { dispatch(ViewerEvent.SilenceLifted) }
            .launchIn(scope)
    }
}
