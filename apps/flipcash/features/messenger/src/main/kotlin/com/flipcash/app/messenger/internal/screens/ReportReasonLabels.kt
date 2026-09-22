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

/**
 * What each reason covers, in one line under its label.
 *
 * Six words of label leave people guessing which bucket their situation belongs in, and a guess
 * resolves as the nearest wrong one — which costs both the reporter and whoever reads it. The
 * descriptions are scope, not policy: they say what the row collects, and promise nothing about
 * what happens next.
 *
 * Exhaustive for the same reason [labelRes] is.
 */
@get:StringRes
internal val ReportReason.descriptionRes: Int
    get() = when (this) {
        ReportReason.Spam -> R.string.description_reportReason_spam
        ReportReason.ScamOrFraud -> R.string.description_reportReason_scamOrFraud
        ReportReason.Harassment -> R.string.description_reportReason_harassment
        ReportReason.SexualContent -> R.string.description_reportReason_sexualContent
        ReportReason.Violence -> R.string.description_reportReason_violence
        ReportReason.Other -> R.string.description_reportReason_other
    }
