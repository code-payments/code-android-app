package com.getcode.view.main.account

import com.getcode.R
import com.getcode.model.FaqItem
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class AccountFaqUiModel(
    val faqItems: List<FaqItem> = listOf()
)

@HiltViewModel
class AccountFaqViewModel @Inject constructor() : BaseViewModel() {
    val uiFlow = MutableStateFlow(AccountFaqUiModel())

    fun reset() {
        val questions = listOf(
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
        )

        val answers = listOf(
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
        )

        val faqItems = questions.mapIndexed { index, i ->
            FaqItem(
                question = getString(i),
                answer = getString(answers[index])
            )
        }

        uiFlow.value = AccountFaqUiModel(faqItems = faqItems)
    }
}