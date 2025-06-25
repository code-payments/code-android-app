package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.services.models.NetworkPoolResolution


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
    fun fromPoolResolution(resolution: PoolResolution.DecisionMade): String = when (resolution) {
        is PoolResolution.BooleanResolution -> "boolean_${resolution.value}"
        PoolResolution.Refund -> "refund"
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