package com.flipcash.services.internal.network

import com.flipcash.services.BlobUploader
import com.flipcash.services.models.blob.UploadTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class HttpBlobUploader @Inject constructor() : BlobUploader {

    private val client = OkHttpClient()

    override suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        target: UploadTarget,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val media = mimeType.toMediaTypeOrNull()
            val request = when (target.method) {
                UploadTarget.Method.PUT -> {
                    Request.Builder()
                        .url(target.url)
                        .apply { target.headers.forEach { (k, v) -> header(k, v) } }
                        .put(bytes.toRequestBody(media))
                        .build()
                }
                UploadTarget.Method.POST -> {
                    // multipart/form-data: the signed policy form fields MUST precede the file part.
                    val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                        target.formFields.forEach { (k, v) -> addFormDataPart(k, v) }
                        addFormDataPart("file", "upload", bytes.toRequestBody(media))
                    }.build()
                    Request.Builder()
                        .url(target.url)
                        .apply { target.headers.forEach { (k, v) -> header(k, v) } }
                        .post(body)
                        .build()
                }
                UploadTarget.Method.UNKNOWN ->
                    throw IllegalStateException("Unknown upload method for target ${target.url}")
            }

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Blob upload failed: HTTP ${response.code}" }
            }
            Unit
        }
    }
}
