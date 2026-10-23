package com.flipcash.services.models

/**
 * The `@` a handle is shown with.
 *
 * Usernames are stored, sent and matched bare — the server's `^[a-z0-9_]{2,15}$` charset has no room
 * for an `@`, and [ProfileIdentifier.Username] / [ResolveIdentifier.Username] carry them that way.
 * The prefix is presentation, so it is added here rather than at each surface that shows one.
 */
const val HandlePrefix = "@"

/** Mirrors the server's `^[a-z0-9_]{2,15}$` validation on `common.v1.Username`. */
const val MinUsernameLength = 2
const val MaxUsernameLength = 15

/**
 * The server's own charset and bounds. Kept here rather than at the one screen that types a
 * username, because two other places have to recognise one without being able to ask the server:
 * the `flipcash.com/<username>` deeplink, which must not mistake `/download` for a handle, and the
 * tip card's link row, which decides whether a URL's last segment is readable or an opaque id.
 */
private val UsernamePattern = Regex("^[a-z0-9_]{$MinUsernameLength,$MaxUsernameLength}$")

/**
 * Whether this could be a username — the charset and length the server accepts, with or without a
 * leading `@`. A yes means only "shaped like one"; whether it is actually claimed is the server's
 * answer, not ours.
 */
fun String.isUsernameShaped(): Boolean = UsernamePattern.matches(removePrefix(HandlePrefix))

/**
 * This username as a handle — `sally_streamer` becomes `@sally_streamer`.
 *
 * Idempotent, so a string that already carries the prefix (a pasted handle, or a social username
 * stored with its `@`) doesn't come back with two.
 */
fun String.asHandle(): String = HandlePrefix + removePrefix(HandlePrefix)

/**
 * The user's public Flipcash handle as displayed, or null when they haven't claimed one.
 *
 * Blank counts as unclaimed: every surface that shows a handle keys off this being null to leave the
 * line out entirely, and a bare `@` is worse than nothing.
 */
val UserProfile.handle: String?
    get() = username?.takeIf { it.isNotBlank() }?.asHandle()

/** The linked X account's handle, shown the same way a Flipcash one is. */
val SocialAccount.TwitterX.handle: String
    get() = username.asHandle()

/**
 * How a person is named: their display name when they have one, their `@handle` when they don't.
 *
 * A `TIP_DM` counterparty is only ever identified by their server profile, and a display name is not
 * required to hold one — `FeedSyncDelegate.feed` keeps name-less tip DMs in the feed on purpose,
 * where a `CONTACT_DM` without an identity is dropped. Every surface that named such a person by
 * `displayName` alone rendered an empty string. The handle is public and stable, so it is the
 * identity to fall back to, and for some of these accounts it is the only one there is.
 *
 * Blank counts as absent on both sides, so a profile carrying `""` reads the same as one carrying
 * nothing. Returns null only when the person has neither — a contact DM's counterparty has no handle
 * by design, so for those this is just the display name.
 */
fun nameOrHandle(displayName: String?, handle: String?): String? =
    displayName?.takeIf { it.isNotBlank() } ?: handle?.takeIf { it.isNotBlank() }
