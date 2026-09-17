package com.flipcash.app.notifications

/**
 * The text a chat push should show once `MessagingStyle` is drawing the sender itself.
 *
 * Pure for the same reason [planSenderLookup] is: the rule is what's worth testing, and stating it
 * needs neither a database nor a live service.
 *
 * The server composes a body that names its sender — "Kevin Ricoy: whoa" — because a plain
 * notification has nowhere else to put it. Under `MessagingStyle` there is: the platform renders
 * the `Person` above the line in a group, and the notification's own title carries it in a DM. The
 * name then appears twice.
 *
 * [senderNames] is every name this sender is known by rather than just the one being displayed,
 * because they can disagree — the line is rendered with the address-book name when there is one,
 * while the server composed the body from the profile's display name. Only an exact match is
 * stripped, so a message that happens to start "Neil: " stays intact unless Neil sent it.
 *
 * @param body the resolved push body, null when the push carries none
 * @param senderNames the sender's known names, in any order; blanks are ignored
 */
fun planMessageBody(body: String?, senderNames: List<String?>): String {
    val text = body.orEmpty()
    val prefix = senderNames
        .asSequence()
        .filterNot { it.isNullOrBlank() }
        .map { "$it:" }
        // Longest first, so a sender known by both a name and a fuller version of it loses the
        // whole thing rather than leaving the remainder behind.
        .sortedByDescending { it.length }
        .firstOrNull { text.startsWith(it, ignoreCase = true) }
        ?: return text

    // A body that is only the prefix isn't an attribution to strip — dropping it would post an
    // empty message.
    return text.substring(prefix.length).trimStart().ifBlank { text }
}
