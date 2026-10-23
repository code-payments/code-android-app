package com.flipcash.services.models.chat

/**
 * A chat's roster — its member list — described without containing it: what a client needs in
 * order to know whether its copy of that list is stale, without holding the list.
 *
 * Says nothing about member profiles; those are hydrated afresh onto every response that carries
 * a member, and a profile change never moves this summary.
 */
data class RosterSummary(
    // Number of currently joined members. For a large group chat, ChatMetadata.members is only
    // a subset of the roster; this is its true size.
    val memberCount: Long,
    // Opaque version, advanced by exactly one on every change to the membership records (a join,
    // a leave, and in future any per-member change such as a role) — never on an idempotent
    // no-op or a profile change. Compare against the last value seen: a different value means the
    // cached member list may be stale and should be refetched. There is no delta to fetch against
    // it, only a refetch of the members.
    val version: Long,
)
