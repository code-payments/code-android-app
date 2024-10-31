package com.getcode.oct24.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.oct24.R
import com.getcode.oct24.data.RoomInfo
import com.getcode.oct24.theme.FlipchatTheme
import com.getcode.theme.CodeTheme
import com.getcode.theme.dropShadow
import com.getcode.ui.utils.Geometry


private class RoomCardGeometry(width: Dp, height: Dp) : Geometry(width, height) {

    val topSpacer: Dp
        get() = size.height * 0.14f

    val iconWidth: Dp
        get() = size.width * 0.2f

    val iconHeight: Dp
        get() = size.width * 0.2f

    val titleTopPadding: Dp
        get() = size.height * 0.14f

    val titleBottomPadding: Dp
        get() = size.height * 0.14f

    val bottomSpacer: Dp
        get() = size.height * 0f
}


@Composable
fun RoomCard(
    modifier: Modifier = Modifier,
    roomInfo: RoomInfo,
) {
    Box(
        modifier = modifier
            .dropShadow(blurRadius = 40.dp, color = Color.Black.copy(alpha = 0.30f))
            .aspectRatio(0.6f)
            .clip(CodeTheme.shapes.small)
            .background(Color(0xFFD9D9D9))
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.14f to roomInfo.gradientColors.first,
                        0.38f to roomInfo.gradientColors.second,
                        0.67f to roomInfo.gradientColors.third
                    ),
                ),
            ).background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(0.4f), Color.Transparent),
                    center = Offset(-100f, -100f),
                    radius = 2000f
                ),
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints {
            val geometry = RoomCardGeometry(maxWidth, maxHeight)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.requiredHeight(geometry.topSpacer))
                Image(
                    modifier = Modifier
                        .size(
                            geometry.iconWidth,
                            geometry.iconHeight
                        ),
                    painter = painterResource(R.drawable.flipchat_logo),
                    contentDescription = null
                )
                Spacer(Modifier.requiredHeight(geometry.titleTopPadding))
                Text(
                    text = roomInfo.title,
                    style = CodeTheme.typography.displaySmall,
                    color = Color.White,
                )
                Spacer(Modifier.requiredHeight(geometry.titleBottomPadding))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = CodeTheme.dimens.grid.x6),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hosted by ${roomInfo.hostName.let { "???" }}",
                        style = CodeTheme.typography.textSmall,
                        color = Color.White.copy(0.80f)
                    )
                    Text(
                        text = "${roomInfo.memberCount} People Inside",
                        style = CodeTheme.typography.textSmall,
                        color = Color.White.copy(0.80f)
                    )
                    Text(
                        text = "Cover Charge: 1,000 Kin",
                        style = CodeTheme.typography.textSmall,
                        color = Color.White.copy(0.80f)
                    )
                }
            }
        }
    }
}

//@Composable
//private fun RoomCardContents(
//    roomInfo: RoomInfo,
//) {
//    BoxWithConstraints(
//        modifier = modifier
//            .dropShadow(blurRadius = 40.dp, color = Color.Black.copy(alpha = 0.30f))
//            .height(IntrinsicSize.Min)
//            .clip(CodeTheme.shapes.small)
//            .background(Color(0xFFD9D9D9))
//            .background(
//                brush = Brush.verticalGradient(
//                    colorStops = arrayOf(
//                        0.14f to roomInfo.gradientColors.first,
//                        0.38f to roomInfo.gradientColors.second,
//                        0.67f to roomInfo.gradientColors.third
//                    ),
//                ),
//            ).background(
//                brush = Brush.radialGradient(
//                    colors = listOf(Color.White.copy(0.4f), Color.Transparent),
//                    center = Offset(-100f, -100f),
//                    radius = 2000f
//                ),
//            ),
//        contentAlignment = Alignment.Center
//    ) {
//        val geometry = RoomCardGeometry(maxWidth, maxHeight)
//        Column(
//            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally,
//        ) {
//            Spacer(Modifier.requiredHeight(geometry.topSpacer))
//            Image(
//                modifier = Modifier
//                    .size(
//                        geometry.iconWidth,
//                        geometry.iconHeight
//                    ),
//                painter = painterResource(R.drawable.flipchat_logo),
//                contentDescription = null
//            )
//            Spacer(Modifier.requiredHeight(geometry.titleTopPadding))
//            Text(
//                text = roomInfo.title,
//                style = CodeTheme.typography.displaySmall,
//                color = Color.White,
//            )
//            Spacer(Modifier.requiredHeight(geometry.titleBottomPadding))
//            Column(
//                modifier = Modifier
//                    .weight(1f)
//                    .padding(horizontal = CodeTheme.dimens.grid.x6),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(
//                    text = "Hosted by ${roomInfo.hostName.let { "???" }}",
//                    style = CodeTheme.typography.textSmall,
//                    color = Color.White.copy(0.80f)
//                )
//                Text(
//                    text = "${roomInfo.memberCount} People Inside",
//                    style = CodeTheme.typography.textSmall,
//                    color = Color.White.copy(0.80f)
//                )
//                Text(
//                    text = "Cover Charge: 1,000 Kin",
//                    style = CodeTheme.typography.textSmall,
//                    color = Color.White.copy(0.80f)
//                )
//            }
//        }
//    }
//}

@Preview
@Composable
private fun Preview_RoomCard() {
    FlipchatTheme {
        Box(modifier = Modifier.size(375.dp, 812.dp)) {
            RoomCard(
                modifier = Modifier.align(Alignment.Center),
                roomInfo = RoomInfo(
                    title = "Room #237",
                    hostName = "Ivy",
                    memberCount = 24,
                )
            )
        }
    }
}