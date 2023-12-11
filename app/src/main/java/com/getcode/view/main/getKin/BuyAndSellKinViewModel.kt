package com.getcode.view.main.getKin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.getcode.R
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class BuyAndSellKinUiModel(
    val items: ImmutableList<BuyAndSellKinItem>
)

data class BuyAndSellKinItem(
    val imageResId: Int,
    val buttonTextResId: Int,
    val link: Uri
)

@HiltViewModel
class BuyAndSellKinViewModel @Inject constructor() : BaseViewModel() {
    private val _state = mutableStateOf(
        BuyAndSellKinUiModel(
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
    )
    val state: State<BuyAndSellKinUiModel> = _state

    fun shareVideo(context: Context, link: Uri) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, link.toString())
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    fun openVideo(context: Context, link: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, link)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}
