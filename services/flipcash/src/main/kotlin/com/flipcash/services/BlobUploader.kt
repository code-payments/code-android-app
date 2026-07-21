package com.flipcash.services

import com.flipcash.services.models.blob.UploadTarget

/**
 * Uploads raw bytes directly to object storage using a presigned [UploadTarget] minted by
 * `BlobStorage.initiateExternalUpload`. The gRPC layer never carries the bytes — this is a plain
 * HTTP PUT (raw body) or POST (multipart/form-data) straight to the storage provider.
 */
interface BlobUploader {
    suspend fun upload(bytes: ByteArray, mimeType: String, target: UploadTarget): Result<Unit>
}
