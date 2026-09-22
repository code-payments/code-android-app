package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.MenuItem
import com.flipcash.core.R as CoreR
import com.flipcash.features.messenger.R

/** What the edit list can open. One entry per editable field on `EditChatRequest`. */
internal sealed interface EditGroupAction {
    data object Picture : EditGroupAction
    data object Name : EditGroupAction
}

/**
 * The group's picture — node 10187:110373's first row, where the design calls it "Icon".
 *
 * Labelled Picture rather than the design's "Icon": it writes `EditChatRequest.picture`, and
 * picture is the word the rest of the app already uses for it.
 *
 * Wears the currency creator's icon glyph. That flow edits the same two fields on a token that this
 * one edits on a group, so the pair reads as one idea rather than two.
 */
internal data object EditGroupPicture : FullMenuItem<EditGroupAction>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_currencycreator_icon)

    override val name: String
        @Composable get() = stringResource(R.string.title_editGroupPicture)

    override val action: EditGroupAction = EditGroupAction.Picture
}

/**
 * The group's title.
 *
 * Not in node 10187:110373 — the design's four rows are Icon, Membership Card, Description and
 * Social Links — but `EditChatRequest.title` is one of exactly two fields the contract offers, so
 * the row exists and takes the picture row's styling rather than inventing its own.
 *
 * Wears the currency creator's name glyph, for the same reason the picture row wears its icon one.
 */
internal data object EditGroupName : FullMenuItem<EditGroupAction>() {
    override val icon: Painter
        @Composable get() = painterResource(CoreR.drawable.ic_currencycreator_name)

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
    listOf(EditGroupPicture, EditGroupName)
