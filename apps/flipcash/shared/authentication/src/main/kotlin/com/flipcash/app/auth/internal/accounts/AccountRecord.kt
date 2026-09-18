package com.flipcash.app.auth.internal.accounts

/**
 * One account in the durable list. Mirrors iOS's `AccountDescription`.
 *
 * Only the entropy is stored — the owner public key and the display name derive from it, which
 * halves the per-record footprint. See the design doc for the 4KB budget.
 */
data class AccountRecord(
    /** Base64-encoded 16-byte seed. */
    val entropy: String,
    val creationDate: Long,
    val lastSeen: Long,
    /** Soft delete. `null` means active, matching iOS's `fetchActiveHistorical`. */
    val deletionDate: Long? = null,
) {
    val isActive: Boolean get() = deletionDate == null

    // The default data class toString() would put a seed into any log line or crash report that
    // touches a record.
    override fun toString(): String = "AccountRecord(created=$creationDate, active=$isActive)"
}
