package com.flipcash.shared.chat.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoPrefetchPolicyTest {

    private val online = PrefetchNetwork(connected = true, dataSaver = false)

    private fun skip(
        redacted: Boolean = false,
        muted: Boolean = false,
        cached: Boolean = false,
        network: PrefetchNetwork = online,
    ) = photoPrefetchSkip(redacted = redacted, muted = muted, cached = cached, network = network)

    @Test
    fun `a new photo on a connected network is fetched`() {
        assertNull(skip())
    }

    @Test
    fun `a redacted photo is not fetched`() {
        assertEquals(PhotoPrefetchSkip.Redacted, skip(redacted = true))
    }

    @Test
    fun `a photo in a muted chat is not fetched`() {
        assertEquals(PhotoPrefetchSkip.Muted, skip(muted = true))
    }

    @Test
    fun `a photo already on disk is not fetched again`() {
        assertEquals(PhotoPrefetchSkip.Cached, skip(cached = true))
    }

    @Test
    fun `nothing is fetched without a network`() {
        assertEquals(PhotoPrefetchSkip.Offline, skip(network = online.copy(connected = false)))
    }

    @Test
    fun `nothing is fetched while Data Saver is on`() {
        assertEquals(PhotoPrefetchSkip.DataSaver, skip(network = online.copy(dataSaver = true)))
    }

    @Test
    fun `the photo's own state is reported before the network's`() {
        val offlineWithSaver = PrefetchNetwork(connected = false, dataSaver = true)

        assertEquals(PhotoPrefetchSkip.Redacted, skip(redacted = true, muted = true, network = offlineWithSaver))
        assertEquals(PhotoPrefetchSkip.Muted, skip(muted = true, cached = true, network = offlineWithSaver))
        assertEquals(PhotoPrefetchSkip.Cached, skip(cached = true, network = offlineWithSaver))
        assertEquals(PhotoPrefetchSkip.Offline, skip(network = offlineWithSaver))
    }
}
