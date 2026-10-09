package com.flipcash.app.messenger.internal.link

/**
 * The preview pictures kept on disk, as the row store sees them: it only ever needs to drop one.
 * An interface so a test of the rows needs no Coil disk.
 */
internal interface WebImageStore {
    /** Deletes the picture stored for [url], the resolved `imageUrl` of a row. A missing one is fine. */
    fun remove(url: String)
}
