package com.flipcash.app.core.money

import kotlinx.serialization.Serializable

@Serializable
enum class RegionSelectionKind {
    Entry,
    Balance;
}