package com.flipcash.app.userflags

import androidx.datastore.preferences.core.Preferences

sealed interface FieldOverride<out T> {
    data object None : FieldOverride<Nothing>
    data class Value<T>(val value: T) : FieldOverride<T>
}

private fun <T> FieldOverride<T>.valueOr(default: T): T = when (this) {
    is FieldOverride.None -> default
    is FieldOverride.Value -> value
}

internal fun <Stored, Domain> Preferences.readOverride(
    field: Field<Stored, Domain>
): FieldOverride<Domain> {
    val stored = this[field.preferenceKey] ?: return FieldOverride.None
    val decoded = field.decode(stored) ?: return FieldOverride.None
    return FieldOverride.Value(decoded)
}