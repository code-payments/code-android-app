package com.getcode.view.main.bill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.LocalDownloadQrCode
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CardFace
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.FlippableCard
import com.getcode.ui.components.Row
import com.getcode.ui.components.TwitterUsernameDisplay
import com.getcode.ui.utils.Geometry
import com.getcode.ui.utils.rememberedLongClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

private class TipCardGeometry(width: Dp, height: Dp) : Geometry(width, height) {

    val backQrSize: Dp
        get() = size.width * 0.40f

    val platformLogoSize: Dp
        @Composable get () = CodeTheme.dimens.staticGrid.x5
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TipCard(
    modifier: Modifier = Modifier,
    username: String,
    data: List<Byte>,
    interactive: Boolean = false,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = CodeTheme.dimens.inset),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.57f, matchHeightConstraintsFirst = true)
                .padding(bottom = screenHeight * 0.05f)
                .padding(top = CodeTheme.dimens.grid.x9)
        ) {

            var cardFace by rememberSaveable { mutableStateOf(CardFace.Front) }
            FlippableCard(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x3)
                    .clickable(
                        enabled = interactive,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        cardFace = cardFace.next
                    },
                cardFace = cardFace,
                back = {
                    BoxWithConstraints {
                        val geometry = remember(maxWidth, maxHeight) {
                            TipCardGeometry(maxWidth, maxHeight)
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(CodeTheme.colors.background)
                        )
                        Back(geometry)
                    }
                },
                front = {
                    BoxWithConstraints {
                        val geometry = remember(maxWidth, maxHeight) {
                            TipCardGeometry(maxWidth, maxHeight)
                        }
                        HazedBackground(geometry = geometry)
                        Front(geometry = geometry, data = data, username = username)
                    }
                }
            )
        }
    }
}

@Composable
private fun HazedBackground(
    geometry: Geometry,
) {
    val hazeState = remember { HazeState() }
    Box(
        modifier = Modifier
            .background(color = Brand)
            .cardBorder()
            .haze(state = hazeState)
    ) {
        val background = CodeTheme.colors.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                // blur
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(blurRadius = geometry.size.width * 0.07f)
                )
                // mimic an inner shadow from Figma
                .drawBehind {
                    inset(
                        horizontal = 2.dp.toPx(),
                        vertical = 2.dp.toPx()
                    ) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(0.45f),
                                    background.copy(alpha = 0.24f)
                                ),
                                center = Offset.Zero,
                                radius = size.maxDimension
                            ),
                        )
                    }
                }
        )
    }
}

@Composable
private fun Front(
    geometry: TipCardGeometry,
    data: List<Byte>,
    username: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        if (data.isNotEmpty()) {
            ScannableCode(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(geometry.codeSize),
                data = data
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TwitterUsernameDisplay(
            modifier = Modifier.fillMaxWidth(),
            username = username
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Back(
    geometry: TipCardGeometry,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.title_tipCardBack),
            style = CodeTheme.typography.textLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        val qrCode = LocalDownloadQrCode.current

        if (qrCode != null) {
            Image(
                modifier = Modifier
                    .size(geometry.backQrSize),
                painter = qrCode,
                contentDescription = "qr"
            )
        } else {
            CodeCircularProgressIndicator()
        }

        Row(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            horizontalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.inset,
                alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier.size(geometry.platformLogoSize),
                painter = painterResource(id = R.drawable.ic_apple_icon),
                contentDescription = null
            )
            Image(
                modifier = Modifier.size(geometry.platformLogoSize),
                painter = painterResource(id = R.drawable.ic_android_icon),
                contentDescription = null
            )
        }


        Spacer(modifier = Modifier.weight(1f))
        Text(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(top = CodeTheme.dimens.grid.x2),
            text = stringResource(R.string.subtitle_tipCardBack),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Modifier.cardBorder() = border(
    BorderStroke(
        1.dp,
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(0xFF15141B),
                1f to Color(0xFF161325)
            )
        )
    )
)