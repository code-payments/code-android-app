package com.getcode.navigation.flow

import androidx.navigation3.runtime.NavKey

/**
 * Marker for types that represent a single step inside a [FlowRoute]'s inner back stack.
 *
 * Per-flow sealed hierarchies should live in the feature module that owns the flow, not in
 * `ui/navigation`.
 */
interface FlowStep : NavKey
