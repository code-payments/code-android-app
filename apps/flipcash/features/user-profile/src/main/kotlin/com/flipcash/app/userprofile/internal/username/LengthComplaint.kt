package com.flipcash.app.userprofile.internal.username

import com.flipcash.services.models.MaxUsernameLength
import com.flipcash.services.models.MinUsernameLength

/**
 * A handle that is the wrong length, raised locally and never off the wire — the server folds both
 * ends into `INVALID_USERNAME`, and the dialog could no longer say which one was wrong.
 *
 * The third of iOS `UsernameValidator.Failure`'s cases, `invalidCharacters`, has no counterpart
 * here: `UsernameInputTransformation` filters the charset as the user types, so the only way to
 * reach that dialog on Android is a server rejection.
 */
internal sealed class LengthComplaint(message: String) : IllegalArgumentException(message) {
    class TooShort : LengthComplaint("Username shorter than $MinUsernameLength characters")
    class TooLong : LengthComplaint("Username longer than $MaxUsernameLength characters")
}

/** The complaint [username] would earn on submit, or null when its length is acceptable. */
internal fun lengthComplaint(username: String): LengthComplaint? = when {
    username.length < MinUsernameLength -> LengthComplaint.TooShort()
    username.length > MaxUsernameLength -> LengthComplaint.TooLong()
    else -> null
}
