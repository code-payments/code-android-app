package com.flipcash.app.menu.internal

import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.services.models.UserProfile

/**
 * The "Finish Your Profile" checklist for the "You" tab.
 *
 * Null while the profile is unresolved, so the card is never drawn against a guess — an account
 * that already has a photo would otherwise flash an outstanding step on the way in. Null again once
 * every step is done: a checklist with nothing left to do is just a row of ticks.
 *
 * Every step reads straight off the profile, so a step completed elsewhere — My Account's own
 * Minimum Tip row, say — closes here too. An account that already has a name sees that step ticked
 * rather than dropped, so the list keeps its length until the whole thing goes away.
 */
internal fun profileTutorialItems(profile: UserProfile?): List<TutorialItem.Profile>? {
    profile ?: return null
    val items = listOf(
        // The same test the tab uses to choose the claimed card over the blurred stand-in.
        TutorialItem.DisplayName(isCompleted = profile.displayName.isNotEmpty()),
        TutorialItem.ProfilePicture(isCompleted = profile.profilePicture != null),
        TutorialItem.MinimumTip(isCompleted = profile.minDmChatInitFee != null),
    )
    return items.takeUnless { steps -> steps.all { it.isCompleted } }
}
