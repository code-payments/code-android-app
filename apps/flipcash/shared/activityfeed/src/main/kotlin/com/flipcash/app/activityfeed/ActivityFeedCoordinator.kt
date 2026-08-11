package com.flipcash.app.activityfeed

@Deprecated(
    "Moved to :shared:transaction-history as part of the v2 (FeatureFlag.NewUi) phase-out. " +
        "Import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator instead. " +
        "Plan: docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md",
    ReplaceWith(
        "ActivityFeedCoordinator",
        "com.flipcash.shared.transactionhistory.ActivityFeedCoordinator",
    ),
)
typealias ActivityFeedCoordinator = com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
