package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.messenger.R

/** What the edit list can open. One entry per editable field on `EditChatRequest`. */
internal sealed interface EditGroupAction {
    data object Picture : EditGroupAction
    data object Name : EditGroupAction
}

/**
 * The group's picture — node 10187:110373's first row, where the design calls it "Icon".
 *
 * "Icon" is the row's own label in the design and is kept, even though the field it writes is
 * `EditChatRequest.Picture` and the rest of the app calls it a picture: the label names what the
 * user sees at the head of the group, not the wire field behind it.
 */
internal data object EditGroupIcon : FullMenuItem<EditGroupAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Image)

    override val name: String
        @Composable get() = stringResource(R.string.title_editGroupIcon)

    override val action: EditGroupAction = EditGroupAction.Picture
}

/**
 * The group's title.
 *
 * Not in node 10187:110373 — the design's four rows are Icon, Membership Card, Description and
 * Social Links — but `EditChatRequest.title` is one of exactly two fields the contract offers, so
 * the row exists and takes the Icon row's styling rather than inventing its own.
 */
internal data object EditGroupName : FullMenuItem<EditGroupAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.TextFields)

    override val name: String
        @Composable get() = stringResource(R.string.title_editGroupName)

    override val action: EditGroupAction = EditGroupAction.Name
}

/**
 * The edit list, in the order node 10187:110373 puts them in — the picture first.
 *
 * A function rather than a constant so the list has one definition that both the screen and its
 * test read. Membership Card, Description and Social Links are deliberately absent: flipcash2
 * 0.11.0's `EditChatRequest` has `title` and `picture` and nothing else, so a row for any of them
 * would be a control with nothing to write.
 */
internal fun editGroupItems(): List<MenuItem<EditGroupAction>> =
    listOf(EditGroupIcon, EditGroupName)
