package com.getcode.view.main.getKin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.NoRippleIndicationWrapper

data class BuyAndSellKinItem(
    val imageResId: Int,
    val buttonTextResId: Int,
    val link: Uri
)

@Composable
fun BuyAndSellKin() {
    val context = LocalContext.current

    val items = listOf(
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
    )

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        val (topSection) = createRefs()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(topSection) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_buyAndSellKin),
                    style = MaterialTheme.typography.h1,
                    modifier = Modifier.padding(vertical = 15.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.subtitle_buySellDescription),
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(vertical = 30.dp)
                )
            }

            items(items, key = { it.link }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVideoClick(
                                context = context,
                                link = item.link
                            )
                        }
                        .padding(bottom = 10.dp)

                ) {
                    Image(
                        modifier = Modifier.clip(MaterialTheme.shapes.small),
                        painter = painterResource(id = item.imageResId),
                        contentDescription = "Video Thumbnail"
                    )

                    Image(
                        modifier = Modifier
                            .size(70.dp)
                            .align(Alignment.Center),
                        painter = painterResource(id = R.drawable.youtube),
                        contentDescription = "Youtube Logo"
                    )
                }
                CodeButton(
                    onClick = {
                        onVideoClick(
                            context = context,
                            link = item.link
                        )
                    },
                    text = stringResource(id = item.buttonTextResId),
                    buttonState = ButtonState.Filled
                )

                NoRippleIndicationWrapper {
                    CodeButton(
                        modifier = Modifier.padding(bottom = 30.dp),
                        onClick = {
                            shareVideo(
                                context = context,
                                link = item.link.toString()
                            )
                        },
                        text = stringResource(id = R.string.action_shareVideo),
                        buttonState = ButtonState.Subtle,
                        isPaddedVertical = false
                    )
                }
            }
        }
    }
}


private fun onVideoClick(
    context: Context,
    link: Uri
) {
    val intent = Intent(Intent.ACTION_VIEW, link)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

private fun shareVideo(
    context: Context,
    link: String
) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, link)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
