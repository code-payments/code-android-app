package com.flipcash.app.notifications

import android.app.usage.UsageStatsManager
import kotlin.test.Test
import kotlin.test.assertEquals

class StandbyBucketReporterTest {

    @Test
    fun `maps every documented bucket to a stable label`() {
        assertEquals("active", bucketLabel(UsageStatsManager.STANDBY_BUCKET_ACTIVE))
        assertEquals("working_set", bucketLabel(UsageStatsManager.STANDBY_BUCKET_WORKING_SET))
        assertEquals("frequent", bucketLabel(UsageStatsManager.STANDBY_BUCKET_FREQUENT))
        assertEquals("rare", bucketLabel(UsageStatsManager.STANDBY_BUCKET_RARE))
        assertEquals("restricted", bucketLabel(UsageStatsManager.STANDBY_BUCKET_RESTRICTED))
    }

    @Test
    fun `maps an unknown bucket to its raw value rather than losing it`() {
        assertEquals("unknown_999", bucketLabel(999))
    }
}
