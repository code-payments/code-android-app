package com.flipcash.app.core.ui.flow

import com.getcode.navigation.flow.FlowStep
import kotlin.reflect.KClass

/**
 * Fraction (0f..1f) of the stepped flow completed at [step], given the ordered [progressSteps]
 * that participate in the progress indicator. Steps outside [progressSteps] (intro, funding,
 * processing, etc.) report 0f so the bar hides. Extracted from CurrencyCreatorViewModel.progress
 * so every stepped flow shares one definition.
 */
fun flowProgressFor(
    step: FlowStep?,
    progressSteps: List<KClass<out FlowStep>>,
): Float {
    if (step == null || progressSteps.isEmpty()) return 0f
    val index = progressSteps.indexOfFirst { it.isInstance(step) }
    if (index < 0) return 0f
    return (index + 1).toFloat() / progressSteps.size
}
