package com.flipcash.analytics

/** Cumulative per-user counters, stored as Mixpanel people properties. */
enum class PeopleCounter(val key: String) {
    TIPS("Tips Received"),
    TIPS_VALUE("Tips Received Value"),
    MESSAGES("Messages Received"),
}
