package com.flipcash.app.messenger.internal.link

import coil3.disk.DiskCache

/** [WebImageStore] over the preview loader's [DiskCache]. */
internal class CoilWebImageStore(private val diskCache: DiskCache?) : WebImageStore {
    override fun remove(url: String) {
        diskCache?.remove(url)
    }
}
