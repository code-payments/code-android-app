package com.flipcash.app.notifications

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Stable string label for a raw [UsageStatsManager] standby bucket constant.
 *
 * Separated from the system lookup so the mapping is testable without a
 * device, and so an unrecognised bucket is preserved rather than collapsed
 * into "unknown" — a new bucket constant would otherwise vanish silently.
 */
fun bucketLabel(bucket: Int): String = when (bucket) {
    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
    UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
    UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
    STANDBY_BUCKET_EXEMPTED -> "exempted"
    else -> "unknown_$bucket"
}

// UsageStatsManager.STANDBY_BUCKET_EXEMPTED is a system API, so apps can't reference it. An app
// the user exempts from battery optimization reports it.
private const val STANDBY_BUCKET_EXEMPTED = 5

/** The calling app's current standby bucket, or "unavailable" if it cannot be read. */
fun Context.currentStandbyBucket(): String {
    val manager = getSystemService<UsageStatsManager>() ?: return "unavailable"
    return runCatching { bucketLabel(manager.appStandbyBucket) }.getOrElse { "unavailable" }
}
