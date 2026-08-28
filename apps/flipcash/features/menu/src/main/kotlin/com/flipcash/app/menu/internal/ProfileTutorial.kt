package com.flipcash.app.menu.internal

import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.services.models.UserProfile

/**
 * The "Finish Your Profile" checklist for the "You" tab (node 9544:18140).
 *
 * Null while the profile is unresolved, so the card is never drawn against a guess — an account
 * that already has a photo would otherwise flash an outstanding step on the way in. Null again once
 * every step is done: a checklist with nothing left to do is just a row of ticks.
 *
 * Both steps read straight off the profile, so a step completed elsewhere — My Account's own
 * Minimum Tip row, say — closes here too.
 */
internal fun profileTutorialItems(profile: UserProfile?): List<TutorialItem.Profile>? {
    profile ?: return null
    val items = listOf(
        TutorialItem.ProfilePicture(isCompleted = profile.profilePicture != null),
        TutorialItem.MinimumTip(isCompleted = profile.minDmChatInitFee != null),
    )
    return items.takeUnless { steps -> steps.all { it.isCompleted } }
}
