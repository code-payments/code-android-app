package com.flipcash.services.models

/**
 * Enables multiple activity feeds, where notifications may be
 * split across different parts of the app
 */
enum class ActivityFeedType {
    Unknown,
    TransactionHistory // Activity feed displayed under the Balance tab
}