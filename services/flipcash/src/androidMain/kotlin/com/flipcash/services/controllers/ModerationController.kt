package com.flipcash.services.controllers

import android.net.Uri
import com.flipcash.services.models.ImageContentKind
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextContentKind
import com.flipcash.services.repository.ModerationRepository
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ContentReader
import javax.inject.Inject

class ModerationController @Inject constructor(
    private val repository: ModerationRepository,
    private val userManager: UserManager,
    private val contentReader: ContentReader,
) {
    /** [kind] is bound into the attestation, which the server then accepts only for that kind. */
    suspend fun moderateText(text: String, kind: TextContentKind): Result<ModerationResult.Text> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.moderateText(text, kind, owner)
    }

    /** [kind] is bound into the attestation, as with [moderateText]. */
    suspend fun moderateImage(uri: Uri, kind: ImageContentKind): Result<ModerationResult.Image> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val imageBytes = contentReader.readBytes(uri)
            ?: return Result.failure(Throwable("Failed to retrieve bytes from image @ $uri"))

        return repository.moderateImage(imageBytes, kind, owner)
    }
}