package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.services.models.NetworkPoolBetOutcome

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