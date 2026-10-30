package com.flipcash.services.models

/**
 * Suggested tip amounts for a given currency/region, sourced from server-side [UserFlags].
 *
 * [region] is a lowercase 3-4 character region/currency code (matching `common.v1.Region.value`).
 * The four amounts are expressed in the region's currency units.
 */
data class TipPresets(
    val region: String,
    val minimum: Double,
    val low: Double,
    val medium: Double,
    val high: Double,
)
