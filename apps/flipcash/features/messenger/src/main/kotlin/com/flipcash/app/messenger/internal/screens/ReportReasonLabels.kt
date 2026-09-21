package com.flipcash.app.messenger.internal.screens

import androidx.annotation.StringRes
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportReason

/**
 * The label for each reason.
 *
 * An exhaustive `when` rather than a field on the enum: [ReportReason] is shared with iOS and
 * compiled for both, and an Android string resource cannot cross that boundary. Exhaustive so a
 * reason added upstream fails the build here instead of shipping a row with no words on it.
 */
@get:StringRes
internal val ReportReason.labelRes: Int
    get() = when (this) {
        ReportReason.Spam -> R.string.action_reportReason_spam
        ReportReason.ScamOrFraud -> R.string.action_reportReason_scamOrFraud
        ReportReason.Harassment -> R.string.action_reportReason_harassment
        ReportReason.SexualContent -> R.string.action_reportReason_sexualContent
        ReportReason.Violence -> R.string.action_reportReason_violence
        ReportReason.Other -> R.string.action_reportReason_other
    }
