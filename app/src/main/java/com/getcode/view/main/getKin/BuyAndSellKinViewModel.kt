package com.getcode.view.main.getKin

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.getcode.R
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject


data class BuyAndSellKinItem(
    val imageResId: Int,
    val buttonTextResId: Int,
    val link: Uri
)

@HiltViewModel
class BuyAndSellKinViewModel @Inject constructor() : BaseViewModel2<BuyAndSellKinViewModel.State, BuyAndSellKinViewModel.Event>(
    initialState = State.Initial,
    updateStateForEvent = updateStateForEvent,
) {

    @Immutable
    data class State(
        val items: ImmutableList<BuyAndSellKinItem>
    ) {
        companion object {
            val Initial = State(
                items = listOf(
                    BuyAndSellKinItem(
                        imageResId = R.drawable.video_buy_kin_2x,
                        buttonTextResId = R.string.action_learnHowToBuyKin,
                        link = Uri.parse("https://www.youtube.com/watch?v=s2aqkF3dJcI")
                    ),
                    BuyAndSellKinItem(
                        imageResId = R.drawable.video_sell_kin_2x,
                        buttonTextResId = R.string.action_learnHowToSellKin,
                        link = Uri.parse("https://www.youtube.com/watch?v=cyb9Da_mV9I")
                    )
                ).toImmutableList()
            )
        }
    }

    sealed interface Event {
        data class ShareVideo(val link: Uri): Event
        data class OpenVideo(val link: Uri): Event
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OpenVideo,
                is Event.ShareVideo -> { state -> state }
            }
        }
    }
}
