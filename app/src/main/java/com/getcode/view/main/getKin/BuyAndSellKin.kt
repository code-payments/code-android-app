package com.getcode.view.main.getKin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun BuyAndSellKin(
    viewModel: BuyAndSellKinViewModel = viewModel()
) {
    val context = LocalContext.current

    val state by viewModel.stateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuyAndSellKinViewModel.Event.OpenVideo>()
            .map { it.link }
            .onEach { openVideo(context, it) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuyAndSellKinViewModel.Event.ShareVideo>()
            .map { it.link }
            .onEach { shareVideo(context, it) }
            .launchIn(this)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset),
    ) {
        val (topSection) = createRefs()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(topSection) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_buyAndSellKin),
                    style = CodeTheme.typography.h1,
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.subtitle_buySellDescription),
                    style = CodeTheme.typography.body1,
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x6),
                )
            }

            items(state.items.toImmutableList(), key = { it.link }) { item ->

                VideoThumbnail(
                    context = context,
                    imageResId = item.imageResId,
                    link = item.link,
                    onVideoClick = { _, link ->
                        viewModel.dispatchEvent(BuyAndSellKinViewModel.Event.OpenVideo(link))
                    },
                )

                CodeButton(
                    onClick = {
                        viewModel.dispatchEvent(BuyAndSellKinViewModel.Event.OpenVideo(item.link))
                    },
                    text = stringResource(id = item.buttonTextResId),
                    buttonState = ButtonState.Filled,
                )

                CodeButton(
                    modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x6),
                    onClick = {
                        viewModel.dispatchEvent(BuyAndSellKinViewModel.Event.ShareVideo(item.link))
                    },
                    text = stringResource(id = R.string.action_shareVideo),
                    buttonState = ButtonState.Subtle,
                    isPaddedVertical = false,
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    context: Context,
    imageResId: Int,
    link: Uri,
    onVideoClick: (Context, Uri) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CodeTheme.dimens.grid.x2)
            .clip(CodeTheme.shapes.small)
            .clickable { onVideoClick(context, link) },

    ) {
        Image(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            contentScale = ContentScale.Fit,
            painter = painterResource(id = imageResId),
            contentDescription = "Video Thumbnail",
        )

        Image(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x14)
                .align(Alignment.Center),
            painter = painterResource(id = R.drawable.youtube),
            contentDescription = "Youtube Logo",
        )
    }
}

private fun shareVideo(context: Context, link: Uri) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, link.toString())
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

private fun openVideo(context: Context, link: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, link)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
