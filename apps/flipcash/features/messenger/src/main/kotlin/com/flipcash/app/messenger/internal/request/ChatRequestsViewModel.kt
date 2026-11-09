package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.deckOrder
import com.flipcash.services.models.paymentrequest.nextExpiry
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.opencode.model.core.ID
import dagger.hilt.android.lifecycle.HiltViewModel
import com.flipcash.shared.chat.ui.request.RequestMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A group chat's payment requests as the conversation screen shows them: the cards in the
 * transcript and the deck under the app bar. Owns the screen's one-minute clock, a tick at the
 * next `ends_at` so an expiring request leaves the deck on time, and the 45 s poll.
 */
@HiltViewModel
internal class ChatRequestsViewModel(
    private val requests: PaymentRequests,
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
    private val clock: Clock,
) : ViewModel() {

    @Inject
    constructor(
        requests: PaymentRequests,
        chatCoordinator: ChatCoordinator,
        userManager: UserManager,
    ) : this(requests, chatCoordinator, userManager, Clock.System)

    val viewerId: ID? get() = userManager.accountId

    private val chatId = MutableStateFlow<ChatId?>(null)

    private val _now = MutableStateFlow(clock.now())

    /** The time for status lines and the ended state, ticking once a minute and at each `ends_at`. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    /** When this screen opened, so a card can tell a message that arrived from one scrolled to. */
    val openedAt: Instant = clock.now()

    /** How many times the deck was swiped since the last refetch. View state only. */
    private val cycled = MutableStateFlow(0)

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    val held: StateFlow<Map<ID, PaymentRequest>> = requests.state
    val missing: StateFlow<Set<ID>> = requests.missing

    private val _deck = MutableStateFlow<List<PaymentRequest>>(emptyList())

    /** The deck, front card first. Empty hides it. */
    val deck: StateFlow<List<PaymentRequest>> = _deck.asStateFlow()

    // Above init: viewModelScope runs on Main.immediate, so the deck collector publishes before
    // the constructor returns, and fields declared after init would still be null.
    private var settle: Job? = null
    private var settledDeck: List<PaymentRequest> = emptyList()
    private var expiry: Job? = null

    init {
        viewModelScope.launch {
            combine(
                chatId.flatMapLatest { id -> id?.let { requests.inChat(it) } ?: flowOf(emptyList()) },
                _now,
                cycled,
            ) { inChat, now, cycled -> Triple(deckOrder(inChat, now, viewerId).rotated(cycled), inChat, now) }
                .collect { (next, inChat, now) ->
                    publish(next, inChat)
                    scheduleExpiry(inChat, now)
                }
        }
        viewModelScope.launch {
            chatId.filterNotNull().flatMapLatest { id ->
                val arrivals = PaymentArrivals(openedAt)
                chatCoordinator.observeMessages(id).map(arrivals::newlyPaid)
            }.collect { paid -> paid.forEach(::refetch) }
        }
        viewModelScope.launch {
            while (true) {
                delay(1.minutes)
                _now.value = clock.now()
            }
        }
    }

    /**
     * Puts [next] on the deck. A request that ends leaves it at once, except a completed one, which
     * stays in its slot while its ring sweeps to full and then leaves; the cards behind close the
     * gap. Changes arriving meanwhile update the cards in place and join the pending settle.
     */
    private fun publish(next: List<PaymentRequest>, inChat: List<PaymentRequest>) {
        val holding = settle?.isActive == true
        val held = holdForCompletion(_deck.value, next, inChat)
        settledDeck = next
        if (held == null) {
            settle?.cancel()
            show(next)
            return
        }
        _deck.value = held
        if (!holding) {
            settle = viewModelScope.launch {
                delay(RequestMotion.CHANGE_MILLIS.toLong())
                show(settledDeck)
            }
        }
    }

    /** An expanded list that loses its last card collapses as the deck fades out. */
    private fun show(deck: List<PaymentRequest>) {
        _deck.value = deck
        if (deck.isEmpty()) _expanded.value = false
    }

    /** Ticks the clock at the next `ends_at`, not the next minute, so an expiring card leaves on time. */
    private fun scheduleExpiry(inChat: List<PaymentRequest>, now: Instant) {
        expiry?.cancel()
        val at = nextExpiry(inChat, now) ?: return
        expiry = viewModelScope.launch {
            delay(at - clock.now())
            _now.value = maxOf(clock.now(), at)
        }
    }

    /**
     * Points the deck at [chat] and fills it from the requests already held, so a chat opened with
     * known requests shows its deck on the first frame instead of fading it in.
     */
    fun attach(chat: ChatId) {
        if (chatId.value == chat) return
        chatId.value = chat
        val held = requests.state.value.values.filter { it.chatId == chat }
        _deck.value = deckOrder(held, _now.value, viewerId).rotated(cycled.value)
    }

    /**
     * Loads [chat]'s requests now and every 45 s until cancelled. Run it while the chat is on
     * screen with the app in the foreground; each pass puts the deck back in its default order.
     */
    suspend fun poll(chat: ChatId) {
        chatId.value = chat
        while (true) {
            requests.loadChat(chat).onSuccess { cycled.value = 0 }
            _now.value = clock.now()
            delay(POLL_INTERVAL)
        }
    }

    fun fetchIfAbsent(id: ID) {
        viewModelScope.launch { requests.fetchIfAbsent(id) }
    }

    /** A failed refetch leaves the held copy as it was; the 45 s poll reconciles it. */
    private fun refetch(id: ID) {
        viewModelScope.launch { runCatching { requests.refetch(id) } }
    }

    /** Swipe down on the deck: the front card goes to the back. */
    fun cycle() {
        if (deck.value.size > 1) cycled.update { it + 1 }
    }

    fun setExpanded(expanded: Boolean) {
        _expanded.value = expanded && deck.value.isNotEmpty()
    }

    companion object {
        val POLL_INTERVAL = 45.seconds
    }
}

internal fun <T> List<T>.rotated(by: Int): List<T> {
    if (size < 2) return this
    val shift = by.mod(size)
    return drop(shift) + take(shift)
}
