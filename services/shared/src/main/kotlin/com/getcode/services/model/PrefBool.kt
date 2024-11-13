@file:Suppress("ClassName")

package com.getcode.services.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefBool(
    @PrimaryKey val key: String,
    val value: Boolean
)

// Used internally to control logic and UI
interface InternalRouting
// Beta flag exposed in Settings -> Beta Flags to enable bleeding edge features
interface BetaFlag
// Dev settings
interface DevSetting
// This removes it from the UI in Settings -> Beta Flags
interface Immutable
// Once a feature behind a beta flag is made public, it becomes immutable
interface Launched: Immutable
// A feature flag can also be deemed deprecated and is also then immutable
interface Deprecated : Immutable
