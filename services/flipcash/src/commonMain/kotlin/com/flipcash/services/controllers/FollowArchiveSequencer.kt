package com.flipcash.services.controllers

/**
 * One stream's FollowArchive bookkeeping. Not thread-safe: the owner serialises access.
 *
 * `seq` rises with every FollowArchive sent on a stream and starts over on the next one, because
 * the server scopes it to the stream. Only the ack for the latest seq says what the stream is
 * doing now; an ack for an earlier seq describes a request a later one already replaced.
 */
internal class FollowArchiveSequencer {
    private var lastSeq = 0L
    private var lastFollow = false

    /** The seq to send with a FollowArchive([follow]) on this stream. */
    fun next(follow: Boolean): Long {
        lastSeq += 1
        lastFollow = follow
        return lastSeq
    }

    /**
     * The follow state the server confirmed with [seq], or null when [seq] is not the latest one
     * sent on this stream.
     */
    fun ack(seq: Long): Boolean? = if (seq != 0L && seq == lastSeq) lastFollow else null

    /** A new stream: nothing has been sent on it. */
    fun reset() {
        lastSeq = 0L
        lastFollow = false
    }
}
