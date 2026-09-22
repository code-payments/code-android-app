package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken

/**
 * One page of `Chat.GetRoster`, most recently joined member first.
 *
 * For a DM or small group this is the exact roster as of [rosterSummary]'s version. For a large
 * group the page can lag [rosterSummary]: merge [members] into a locally held roster per member,
 * by [ChatMember.version] — the greater value wins. A cached member absent from a page is only
 * gone once the roster has been read to the end (`hasMore == false`) *and* the client holds no
 * join for that member at a version above this page's [rosterSummary] version; otherwise the
 * absence just means that member's page has not been reached yet.
 */
data class RosterPage(
    val members: List<ChatMember>,
    // The roster's staleness watermark as of this fetch. Compare like RosterSummary elsewhere:
    // a page can be behind this value for a large group (see class doc), never ahead of it.
    val rosterSummary: RosterSummary,
    // Opaque, server-generated, bound to the chat this page was fetched for. Present when
    // hasMore is true; pass back as QueryOptions.token to continue paging.
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
