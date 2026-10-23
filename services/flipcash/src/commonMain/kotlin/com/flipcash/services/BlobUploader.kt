package com.flipcash.services

import com.flipcash.services.models.blob.UploadTarget

/**
 * Uploads raw bytes directly to object storage using a presigned [UploadTarget] minted by
 * `BlobStorage.initiateExternalUpload`. The gRPC layer never carries the bytes — this is a plain
 * HTTP PUT (raw body) or POST (multipart/form-data) straight to the storage provider.
 */
interface BlobUploader {
    /**
     * [onProgress] reports `(sentBytes, totalBytes)` over the whole request body — for a multipart
     * POST that includes the form fields — as it is written to the socket. It runs on the upload
     * thread and may fire many times, so keep it cheap.
     */
    suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        target: UploadTarget,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<Unit>
}
