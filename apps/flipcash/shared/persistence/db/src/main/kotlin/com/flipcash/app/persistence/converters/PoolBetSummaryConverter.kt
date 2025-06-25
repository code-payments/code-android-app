package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.core.pools.PoolBetSummary
import com.flipcash.services.models.NetworkPoolBetSummary

object PoolBetSummaryConverter {
    @TypeConverter
    fun fromPoolBetSummary(summary: PoolBetSummary): String {
        return when (summary) {
            is PoolBetSummary.Boolean -> "boolean_${summary.numYes}_${summary.numNo}"
            PoolBetSummary.NotSet -> "not-set"
        }
    }

    @TypeConverter
    fun fromPoolBetSummary(summary: NetworkPoolBetSummary): String {
        return when (summary) {
            is NetworkPoolBetSummary.Boolean -> "boolean_${summary.yes}_${summary.no}"
            NetworkPoolBetSummary.NotSet -> "not-set"
        }
    }


    @TypeConverter
    fun toPoolBetSummary(summary: NetworkPoolBetSummary): PoolBetSummary {
        return when (summary) {
            is NetworkPoolBetSummary.Boolean -> PoolBetSummary.Boolean(summary.yes, summary.no)
            NetworkPoolBetSummary.NotSet -> PoolBetSummary.NotSet
        }
    }

    @TypeConverter
    fun toPoolBetSummary(value: String?): PoolBetSummary {
        val parts = value?.split("_").orEmpty()
        return when (parts[0]) {
            "not_set" -> PoolBetSummary.NotSet
            "boolean" -> PoolBetSummary.Boolean(parts[1].toInt(), parts[2].toInt())
            else -> throw IllegalArgumentException("Unknown PoolBetSummary type: $value")
        }
    }
}