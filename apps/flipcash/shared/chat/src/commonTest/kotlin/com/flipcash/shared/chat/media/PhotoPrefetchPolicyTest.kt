package com.flipcash.shared.chat.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoPrefetchPolicyTest {

    private val wifi = PrefetchNetwork(connected = true, dataSaver = false, roaming = false)

    private fun skip(
        redacted: Boolean = false,
        muted: Boolean = false,
        cached: Boolean = false,
        network: PrefetchNetwork = wifi,
    ) = photoPrefetchSkip(redacted = redacted, muted = muted, cached = cached, network = network)

    @Test
    fun `a new photo on an unmetered or home network is fetched`() {
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
        assertEquals(PhotoPrefetchSkip.Offline, skip(network = wifi.copy(connected = false)))
    }

    @Test
    fun `nothing is fetched while Data Saver is on`() {
        assertEquals(PhotoPrefetchSkip.DataSaver, skip(network = wifi.copy(dataSaver = true)))
    }

    @Test
    fun `nothing is fetched on a roaming network`() {
        assertEquals(PhotoPrefetchSkip.Roaming, skip(network = wifi.copy(roaming = true)))
    }

    @Test
    fun `the photo's own state is reported before the network's`() {
        val roamingWithSaver = PrefetchNetwork(connected = true, dataSaver = true, roaming = true)

        assertEquals(PhotoPrefetchSkip.Redacted, skip(redacted = true, muted = true, network = roamingWithSaver))
        assertEquals(PhotoPrefetchSkip.Muted, skip(muted = true, cached = true, network = roamingWithSaver))
        assertEquals(PhotoPrefetchSkip.Cached, skip(cached = true, network = roamingWithSaver))
        assertEquals(PhotoPrefetchSkip.DataSaver, skip(network = roamingWithSaver))
    }
}
