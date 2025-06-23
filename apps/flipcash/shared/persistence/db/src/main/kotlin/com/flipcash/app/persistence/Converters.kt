package com.flipcash.app.persistence

import androidx.room.TypeConverter
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution

object BetOutcomeConverter {
    @TypeConverter
    fun fromBetOutcome(outcome: PoolBetOutcome): String = when (outcome) {
        is PoolBetOutcome.NotSet -> "not_set"
        is PoolBetOutcome.BooleanOutcome -> "boolean_${outcome.value}"
    }

    @TypeConverter
    fun fromBetOutcome(outcome: NetworkPoolBetOutcome): String = when (outcome) {
        is NetworkPoolBetOutcome.NotSet -> "not_set"
        is NetworkPoolBetOutcome.BooleanOutcome -> "boolean_${outcome.value}"
    }

    @TypeConverter
    fun toBetOutcome(value: String?): PoolBetOutcome = when {
        value == null -> PoolBetOutcome.NotSet
        value == "not_set" -> PoolBetOutcome.NotSet
        value.startsWith("boolean_") -> {
            val boolValue = value.removePrefix("boolean_").toBoolean()
            PoolBetOutcome.BooleanOutcome(boolValue)
        }
        else -> throw IllegalArgumentException("Unknown BetOutcome: $value")
    }

    @TypeConverter
    fun toBetOutcome(value: NetworkPoolBetOutcome?): PoolBetOutcome = when (value) {
        null -> PoolBetOutcome.NotSet
        NetworkPoolBetOutcome.NotSet -> PoolBetOutcome.NotSet
        is NetworkPoolBetOutcome.BooleanOutcome -> PoolBetOutcome.BooleanOutcome(value.value)
    }

    @TypeConverter
    fun toBetOutcome(value: PoolBetOutcome?): NetworkPoolBetOutcome = when (value) {
        PoolBetOutcome.NotSet -> NetworkPoolBetOutcome.NotSet
        is PoolBetOutcome.BooleanOutcome -> NetworkPoolBetOutcome.BooleanOutcome(value.value)
        null -> NetworkPoolBetOutcome.NotSet
    }
}

object PoolResolutionConverter {
    @TypeConverter
    fun fromPoolResolution(resolution: NetworkPoolResolution): String = when (resolution) {
        is NetworkPoolResolution.BooleanResolution -> "boolean_${resolution.value}"
        NetworkPoolResolution.NotSet -> "not_set"
        NetworkPoolResolution.Refund -> "refund"
    }

    @TypeConverter
    fun fromPoolResolution(resolution: PoolResolution): String = when (resolution) {
        is PoolResolution.BooleanResolution -> "boolean_${resolution.value}"
        PoolResolution.Refund -> "refund"
        PoolResolution.NotSet -> "not_set"
    }

    @TypeConverter
    fun toPoolResolution(value: String?): PoolResolution = when {
        value == null -> PoolResolution.NotSet
        value == "not_set" -> PoolResolution.NotSet
        value == "refund" -> PoolResolution.Refund
        value.startsWith("boolean_") -> {
            val boolValue = value.removePrefix("boolean_").toBoolean()
            PoolResolution.BooleanResolution(boolValue)
        }
        else -> throw IllegalArgumentException("Unknown PoolResolution: $value")
    }

    @TypeConverter
    fun toPoolResolution(value: NetworkPoolResolution?): PoolResolution = when (value) {
        null -> PoolResolution.NotSet
        NetworkPoolResolution.NotSet -> PoolResolution.NotSet
        is NetworkPoolResolution.BooleanResolution -> PoolResolution.BooleanResolution(value.value)
        NetworkPoolResolution.Refund -> PoolResolution.Refund
    }

    @TypeConverter
    fun toPoolResolution(value: PoolResolution?): NetworkPoolResolution = when (value) {
        null -> NetworkPoolResolution.NotSet
        PoolResolution.NotSet -> NetworkPoolResolution.NotSet
        is PoolResolution.BooleanResolution -> NetworkPoolResolution.BooleanResolution(value.value)
        PoolResolution.Refund -> NetworkPoolResolution.Refund
    }
}