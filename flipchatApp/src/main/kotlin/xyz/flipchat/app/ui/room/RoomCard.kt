package xyz.flipchat.app.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.model.Currency
import xyz.flipchat.app.R
import xyz.flipchat.app.theme.FlipchatTheme
import com.getcode.theme.CodeTheme
import com.getcode.theme.dropShadow
import com.getcode.ui.utils.Geometry
import com.getcode.util.resources.LocalResources
import com.getcode.utils.Kin
import com.getcode.utils.decodeBase58
import com.getcode.utils.formatAmountString
import xyz.flipchat.app.data.RoomInfo


private class RoomCardGeometry(width: Dp, height: Dp) : Geometry(width, height) {

    val topSpacer: Dp
        get() = size.height * 0.06f

    val iconWidth: Dp
        get() = size.width * 0.2f

    val iconHeight: Dp
        get() = size.width * 0.2f

    val titleTopPadding: Dp
        get() = size.height * 0.2f

    val titleBottomPadding: Dp
        get() = size.height * 0.2f

    val bottomSpacer: Dp
        get() = size.height * 0.1f
}


@Composable
fun RoomCard(
    modifier: Modifier = Modifier,
    roomInfo: RoomInfo,
) {
    Box(
        modifier = modifier
            .dropShadow(blurRadius = 40.dp, color = Color.Black.copy(alpha = 0.30f))
            .aspectRatio(0.65f)
            .clip(CodeTheme.shapes.small)
            .background(Color(0xFFD9D9D9))
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.14f to roomInfo.gradientColors.first,
                        0.38f to roomInfo.gradientColors.second,
                        0.67f to roomInfo.gradientColors.third,
                    ),
                ),
            ).background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(0.65f), Color.Transparent),
                    center = Offset(-200f, -200f),
                    radius = 1800f
                ),
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints {
            val geometry = RoomCardGeometry(maxWidth, maxHeight)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
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
                        .padding(horizontal = CodeTheme.dimens.grid.x6),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (roomInfo.hostName != null) {
                        Text(
                            text = stringResource(R.string.title_roomCardHostedBy, roomInfo.hostName),
                            style = CodeTheme.typography.textSmall,
                            color = Color.White.copy(0.80f)
                        )
                    }
                    Text(
                        text = pluralStringResource(R.plurals.title_roomCardMemberCount, roomInfo.memberCount, roomInfo.memberCount),
                        style = CodeTheme.typography.textSmall,
                        color = Color.White.copy(0.80f)
                    )
                    Text(
                        text = stringResource(R.string.title_roomCardJoinCost,
                            formatAmountString(
                                resources = LocalResources.current!!,
                                currency = Currency.Kin,
                                amount = roomInfo.coverCharge.quarks.toDouble(),
                                suffix = stringResource(R.string.core_kin)
                            )
                        ),
                        textAlign = TextAlign.Center,
                        style = CodeTheme.typography.textSmall,
                        color = Color.White.copy(0.80f)
                    )
                }
                Spacer(Modifier.requiredHeight(geometry.bottomSpacer))
            }
        }
    }
}

val id = "4T7DtS9CEZKVJrBgujQLcjBYnMqZSzZV6CqJewME6zVp".decodeBase58().toList()

@Preview
@Composable
private fun Preview_RoomCard() {
    FlipchatTheme {
        Box(modifier = Modifier.size(375.dp, 812.dp)) {
            RoomCard(
                modifier = Modifier.align(Alignment.Center),
                roomInfo = RoomInfo(
                    id = id,
                    title = "Room #237",
                    hostName = "Ivy",
                    memberCount = 24,
                )
            )
        }
    }
}