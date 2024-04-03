package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.Geometry
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TipCard(
    modifier: Modifier = Modifier,
    username: String,
    data: List<Byte>,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = CodeTheme.dimens.inset),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(0.57f, matchHeightConstraintsFirst = true)
                .fillMaxHeight(0.6f)
                .padding(top = screenHeight * 0.10f)
                .clip(RectangleShape)
        ) {
            val geometry = remember(maxWidth, maxHeight) {
                Geometry(maxWidth, maxHeight)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x3),
            ) {
                HazedBackground(geometry = geometry)
                Contents(geometry = geometry, data = data, username = username)
            }
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
            .border(1.dp, Color.Black)
            .background(color = Brand,)
            .haze(state = hazeState)
    ) {
        val background = CodeTheme.colors.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                // mimic an inner shadow from Figma
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(0.55f), background.copy(alpha = 0.44f)),
                            center = Offset(-(geometry.size.width.value * 0.34f), geometry.size.height.value * 0.4f),
                            radius = geometry.diagonalDistance * 1.85f
                        ),
                        topLeft = Offset(geometry.size.width.value * 0.1f, geometry.size.height.value * 0.05f)
                    )
                }
                // blur
                .hazeChild(state = hazeState, style = HazeStyle(blurRadius = geometry.size.width * 0.07f))
        )
    }
}

@Composable
private fun Contents(
    geometry: Geometry,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
                contentDescription = null
            )
            Text(text = username, style = CodeTheme.typography.subtitle1)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}