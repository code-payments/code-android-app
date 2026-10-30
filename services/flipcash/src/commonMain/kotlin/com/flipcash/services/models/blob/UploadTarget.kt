package com.flipcash.services.models.blob

import com.flipcash.services.models.chat.BlobId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A short-lived, presigned target for a direct-to-storage upload. The client issues the described
 * HTTP request (PUT raw body, or POST multipart with [formFields]) straight to [url] — the server
 * never proxies the bytes. Treat it as a bearer credential: do not persist or share it, and re-mint
 * via [BlobStorageController.initiateExternalUpload][com.flipcash.services.controllers.BlobStorageController.initiateExternalUpload]
 * once [expiresAt] passes.
 */
@Serializable
data class UploadTarget(
    val method: Method,
    val url: String,
    val headers: Map<String, String>,
    val formFields: Map<String, String>,
    val expiresAt: Instant,
) {
    enum class Method { UNKNOWN, PUT, POST }
}

/**
 * The result of reserving an upload: the durable [blobId] handle to reference the bytes once
 * uploaded, and the presigned [target] to upload them to.
 */
@Serializable
data class UploadReservation(
    val blobId: BlobId,
    val target: UploadTarget,
)
