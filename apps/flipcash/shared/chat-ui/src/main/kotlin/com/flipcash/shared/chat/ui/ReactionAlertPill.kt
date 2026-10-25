package com.flipcash.shared.chat.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.core.R
import com.flipcash.shared.chat.alerts.ReactionAlertContent
import com.flipcash.shared.chat.alerts.ReactionAlertContent.Kind

/** The pill's three resting shapes. The host walks Rest -> Compact -> Expanded and back. */
enum class ReactionAlertPhase { Rest, Compact, Expanded }

private val PillBlack = Color.Black
private val TextMain = Color.White
private val TextDim = Color.White.copy(alpha = 0.5f)
private val BoostGreen = Color(0xFF34C759)
private val AvatarPalette = listOf(Color(0xFFE8996B), Color(0xFF739EF2), Color(0xFF8CD18C))

/** Figma `Alert/Reaction pill` (node 12643:117): widths from the Compact and Expanded variants. */
object ReactionAlertPillSpec {
    val CompactWidth = 236.dp
    val ExpandedWidth = 378.dp
    val CompactHeight = 36.dp
    val ExpandedRadius = 44.dp

    /** Motion table: grow width 0-180 ms, then open on a spring (stiffness 380, damping 0.78). */
    const val GROW_MILLIS = 180
    const val STIFFNESS = 380f
    const val DAMPING = 0.78f

    fun <T> openSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = DAMPING, stiffness = STIFFNESS)
}

/**
 * The alert pill, drawn for [phase]. [restWidth] x [restHeight] is the island or cutout it grows out
 * of; [expandedTopInset] pushes the expanded content below a corner hole.
 *
 * With [animate] false the sizes jump, which is Reduce Motion's crossfade-in-place and the
 * screenshot tests' fixed frame.
 */
@Composable
fun ReactionAlertPill(
    content: ReactionAlertContent,
    phase: ReactionAlertPhase,
    inActiveChat: Boolean,
    modifier: Modifier = Modifier,
    restWidth: Dp = 125.dp,
    restHeight: Dp = 36.dp,
    expandedTopInset: Dp = 0.dp,
    maxWidth: Dp = ReactionAlertPillSpec.ExpandedWidth,
    animate: Boolean = true,
) {
    val expandedWidth = minOf(ReactionAlertPillSpec.ExpandedWidth, maxWidth)
    val targetWidth = when (phase) {
        ReactionAlertPhase.Rest -> restWidth
        ReactionAlertPhase.Compact -> minOf(ReactionAlertPillSpec.CompactWidth, expandedWidth)
        ReactionAlertPhase.Expanded -> expandedWidth
    }
    val targetRadius = when (phase) {
        ReactionAlertPhase.Rest -> restHeight / 2
        ReactionAlertPhase.Compact -> ReactionAlertPillSpec.CompactHeight / 2
        ReactionAlertPhase.Expanded -> ReactionAlertPillSpec.ExpandedRadius
    }
    val widthSpec: FiniteAnimationSpec<Dp> = if (animate) {
        if (phase == ReactionAlertPhase.Expanded) ReactionAlertPillSpec.openSpring()
        else tween(ReactionAlertPillSpec.GROW_MILLIS)
    } else {
        tween(0)
    }
    val width by animateDpAsState(targetWidth, widthSpec, label = "alertWidth")
    val radius by animateDpAsState(
        targetRadius,
        if (animate) ReactionAlertPillSpec.openSpring() else tween(0),
        label = "alertRadius",
    )

    Box(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(radius))
            .background(PillBlack)
            .then(if (animate) Modifier.animateContentSize(ReactionAlertPillSpec.openSpring()) else Modifier),
    ) {
        Crossfade(
            targetState = phase,
            animationSpec = tween(if (animate) 120 else 0),
            label = "alertPhase",
        ) { shown ->
            when (shown) {
                ReactionAlertPhase.Rest -> Spacer(Modifier.height(restHeight))
                ReactionAlertPhase.Compact -> CompactRow(content)
                ReactionAlertPhase.Expanded -> ExpandedColumn(content, inActiveChat, expandedTopInset)
            }
        }
    }
}

@Composable
private fun CompactRow(content: ReactionAlertContent) {
    Row(
        modifier = Modifier
            .height(ReactionAlertPillSpec.CompactHeight)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AlertAvatar(content.actorNames.first(), index = 0, size = 24.dp, textSize = 10)
        Spacer(Modifier.weight(1f))
        when (content.kind) {
            Kind.Boost -> Text(
                text = boostCentsText(content.boostCents ?: 0, wholeDollars = true),
                color = BoostGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            else -> Text(
                text = content.emojis.last(),
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ExpandedColumn(content: ReactionAlertContent, inActiveChat: Boolean, topInset: Dp) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp + topInset, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidthCompat(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (inActiveChat) stringResource(R.string.alert_thisChat) else content.chatName.orEmpty(),
                color = TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.alert_now),
                color = TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidthCompat(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (content.kind == Kind.Several) {
                AvatarStack(content)
            } else {
                AlertAvatar(content.actorNames.first(), index = 0, size = 40.dp, textSize = 16)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title(content),
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (content.quote.isNotEmpty()) {
                    Text(
                        text = "“${content.quote}”",
                        color = TextDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (content.kind) {
                Kind.Reaction -> Text(content.emojis.last(), color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Kind.Boost -> Text(
                    text = "+" + boostCentsText(content.boostCents ?: 0),
                    color = BoostGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Kind.Several -> Text(
                    text = content.trailingEmojis.joinToString(""),
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun title(content: ReactionAlertContent): String {
    val first = content.actorNames.firstOrNull() ?: stringResource(R.string.alert_someone)
    return when (content.kind) {
        Kind.Reaction -> stringResource(R.string.alert_reaction_title, first)
        Kind.Boost -> stringResource(R.string.alert_boost_title, first, content.emojis.last())
        Kind.Several -> {
            val others = content.actorNames.size - 1
            pluralStringResource(R.plurals.alert_several_title, others, first, others)
        }
    }
}

@Composable
private fun AvatarStack(content: ReactionAlertContent) {
    Box {
        val shown = content.actorNames.take(3)
        shown.forEachIndexed { index, name ->
            AlertAvatar(
                name = name,
                index = index,
                size = 40.dp,
                textSize = 16,
                ring = true,
                modifier = Modifier.offset(x = 26.dp * index),
            )
        }
        // Reserve the stack's width: the avatars are offset, so Box would measure only the first.
        Spacer(Modifier.width(40.dp + 26.dp * (shown.size - 1)).height(40.dp))
    }
}

/** The actor's initial on a palette fill. Swap for the profile photo when one is available. */
@Composable
private fun AlertAvatar(
    name: String?,
    index: Int,
    size: Dp,
    textSize: Int,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
) {
    val fill = AvatarPalette[(name?.hashCode()?.let { Math.floorMod(it, AvatarPalette.size) } ?: index)]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ring) PillBlack else fill)
            .padding(if (ring) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name?.trim()?.firstOrNull()?.uppercase() ?: "?",
            color = Color.Black,
            fontSize = textSize.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Modifier.fillMaxWidthCompat(): Modifier = this.then(Modifier.fillMaxWidth())
