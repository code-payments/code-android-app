package com.flipcash.shared.chat.media

/** The network a prefetch would download over, as far as the prefetch policy cares. */
data class PrefetchNetwork(
    val connected: Boolean,
    /** The user turned on Data Saver, which restricts background data for this app. */
    val dataSaver: Boolean,
    /** The active network is roaming. Wi-Fi and home cellular both count as not roaming. */
    val roaming: Boolean,
)

/** Why a photo that just arrived is not downloaded ahead of the chat being opened. */
enum class PhotoPrefetchSkip {
    /** The bubble draws only the BlurHash and loads nothing. */
    Redacted,

    /** The chat is muted, so its photos are not worth data before the user looks. */
    Muted,

    /** The blob is already in the disk cache. */
    Cached,
    Offline,
    DataSaver,
    Roaming,
}

/**
 * Whether a newly arrived photo should be downloaded before the user opens its chat, or why not.
 * The photo's own state is reported before the network's, so a log line names the reason that would
 * still hold on a better network.
 */
fun photoPrefetchSkip(
    redacted: Boolean,
    muted: Boolean,
    cached: Boolean,
    network: PrefetchNetwork,
): PhotoPrefetchSkip? = when {
    redacted -> PhotoPrefetchSkip.Redacted
    muted -> PhotoPrefetchSkip.Muted
    cached -> PhotoPrefetchSkip.Cached
    !network.connected -> PhotoPrefetchSkip.Offline
    network.dataSaver -> PhotoPrefetchSkip.DataSaver
    network.roaming -> PhotoPrefetchSkip.Roaming
    else -> null
}
