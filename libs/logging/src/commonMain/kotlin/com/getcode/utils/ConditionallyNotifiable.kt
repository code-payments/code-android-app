package com.getcode.utils

/**
 * Interface for errors where notifiability depends on runtime state.
 * [ErrorUtils] checks [isNotifiable] to decide whether to report to Bugsnag.
 */
interface ConditionallyNotifiable {
    val isNotifiable: Boolean
}
