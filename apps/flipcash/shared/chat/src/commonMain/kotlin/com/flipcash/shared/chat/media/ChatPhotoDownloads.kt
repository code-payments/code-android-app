package com.flipcash.shared.chat.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The chat photo downloads running in this process, one per [ChatPhoto.cacheKey], so loads of the
 * same photo at once share a download: a bubble's and the prefetch of its message, or two bubbles'.
 *
 * A download runs on [scope], not on the load that started it, so a load that is cancelled leaves it
 * running for the loads still waiting. The last load to leave cancels it.
 */
internal class ChatPhotoDownloads(private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Download>()

    /**
     * One download and the loads waiting on it. [steps] is the download's own trace, written only by
     * the download; a load reads it once the download has ended.
     */
    private class Download(val steps: ChatPhotoLoadTrace, val bytes: Deferred<Result<ByteArray>>) {
        var waiters = 0
    }

    /**
     * The opened bytes of [photo]: from the download already running for it, else from [download],
     * started now. [download] records its steps on the trace it is given, and [record] takes them
     * when this load ends: all of them once the download has ended, else only the step it was on.
     * A load that found the download running is marked [ChatPhotoLoadTrace.joined].
     */
    suspend fun await(
        photo: ChatPhoto,
        record: ChatPhotoLoadTrace,
        download: suspend (steps: ChatPhotoLoadTrace) -> ByteArray,
    ): ByteArray {
        val key = photo.cacheKey
        val shared = lock.withLock {
            val running = inFlight[key]
            record.joined = running != null
            (running ?: start(key, photo, download)).also { it.waiters++ }
        }
        try {
            // A Result, not the exception through await(), so the load rethrows the download's own exception.
            return shared.bytes.await().getOrThrow()
        } finally {
            withContext(NonCancellable) { leave(key, shared, record) }
        }
    }

    /** Called with [lock] held. */
    private fun start(key: String, photo: ChatPhoto, download: suspend (ChatPhotoLoadTrace) -> ByteArray): Download {
        val steps = ChatPhotoLoadTrace(photo)
        val bytes = scope.async {
            val self = coroutineContext.job
            try {
                Result.success(download(steps))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                // Out of the map before any load sees the result, so a load after a failure starts afresh.
                withContext(NonCancellable) {
                    lock.withLock { if (inFlight[key]?.bytes === self) inFlight.remove(key) }
                }
            }
        }
        return Download(steps, bytes).also { inFlight[key] = it }
    }

    private suspend fun leave(key: String, shared: Download, record: ChatPhotoLoadTrace) {
        val last = lock.withLock {
            shared.waiters--
            val last = shared.waiters == 0
            if (last && inFlight[key] === shared) inFlight.remove(key)
            last
        }
        // A cancel lands on a blocking read only once it returns, so this waits for the whole download,
        // as the load did before downloads were shared.
        if (last) shared.bytes.cancelAndJoin()
        if (shared.bytes.isCompleted) record.adopt(shared.steps) else record.step = shared.steps.step
    }
}
