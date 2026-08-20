package com.flipcash.app.activityfeed

@Deprecated(
    "Moved to :shared:transaction-history as part of the v2 UI phase-out. " +
        "Import com.flipcash.shared.transactionhistory.ActivityFeedUpdater instead. " +
        "Plan: docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md",
    ReplaceWith(
        "ActivityFeedUpdater",
        "com.flipcash.shared.transactionhistory.ActivityFeedUpdater",
    ),
)
typealias ActivityFeedUpdater = com.flipcash.shared.transactionhistory.ActivityFeedUpdater
