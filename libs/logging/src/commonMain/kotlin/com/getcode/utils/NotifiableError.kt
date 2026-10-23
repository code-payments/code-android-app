package com.getcode.utils

/**
 * Marker interface for errors representing unexpected failures (not user-caused).
 * Errors implementing this are tagged in Bugsnag with metadata that triggers Slack notifications.
 */
interface NotifiableError : ConditionallyNotifiable {
    override val isNotifiable: Boolean get() = true
}
