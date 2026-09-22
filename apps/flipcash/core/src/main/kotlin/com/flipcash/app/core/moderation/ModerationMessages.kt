package com.flipcash.app.core.moderation

import androidx.annotation.StringRes
import com.flipcash.core.R
import com.flipcash.services.models.ModerationResult

/**
 * The app's wording for a moderation refusal, one message per [ModerationResult.FlaggedCategory].
 *
 * Extracted from `CreateGroupViewModel` so the group *edit* flow refuses a title in the same words
 * group *creation* does. The strings are deliberately the profile-name ones: they are written
 * about a rejected piece of user text rather than about a profile, and the caller supplies the
 * title that says which piece it was.
 *
 * [NONE] means the server flagged the content without naming a category, so it gets the generic
 * "try a different one" rather than a reason the response never gave.
 */
@StringRes
fun moderationDescription(category: ModerationResult.FlaggedCategory): Int =
    when (category) {
        ModerationResult.FlaggedCategory.NONE ->
            R.string.error_description_imageNotAllowed

        ModerationResult.FlaggedCategory.OTHER ->
            R.string.error_description_profileNameNotAllowedFlaggedOther

        ModerationResult.FlaggedCategory.NSFW ->
            R.string.error_description_profileNameNotAllowedFlaggedNsfw

        ModerationResult.FlaggedCategory.IMPERSONATION ->
            R.string.error_description_profileNameNotAllowedFlaggedImpersonation

        ModerationResult.FlaggedCategory.MISLEADING ->
            R.string.error_description_profileNameNotAllowedFlaggedMisleading

        ModerationResult.FlaggedCategory.SPAM ->
            R.string.error_description_profileNameNotAllowedFlaggedSpam
    }
