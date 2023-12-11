package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.model.FaqItem
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountFaqViewModel @Inject constructor(
    resources: ResourceHelper,
) : BaseViewModel2<AccountFaqViewModel.State, AccountFaqViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    private val questions = listOf(
        R.string.faq_q__1,
        R.string.faq_q__2,
        R.string.faq_q__3,
        R.string.faq_q__4,
        R.string.faq_q__5,
        R.string.faq_q__6,
        R.string.faq_q__7,
        R.string.faq_q__8,
        R.string.faq_q__9,
        R.string.faq_q__10,
        R.string.faq_q__11,
    ).map { resources.getString(it) }

    private val answers = listOf(
        R.string.faq_a__1,
        R.string.faq_a__2,
        R.string.faq_a__3,
        R.string.faq_a__4,
        R.string.faq_a__5,
        R.string.faq_a__6,
        R.string.faq_a__7,
        R.string.faq_a__8,
        R.string.faq_a__9,
        R.string.faq_a__10,
        R.string.faq_a__11,
    ).map { resources.getString(it) }

    private val faqItems = questions.zip(answers).map { FaqItem(question = it.first, answer = it.second) }

    data class State(
        val faqItems: List<FaqItem> = emptyList()
    )

    sealed interface Event {
        data class LoadWith(val items: List<FaqItem>): Event
    }

    init {
        viewModelScope.launch {
            dispatchEvent(Event.LoadWith(faqItems))
        }
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.LoadWith -> { state -> state.copy(faqItems = event.items) }
            }
        }
    }
}