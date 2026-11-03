package com.flipcash.shared.chat.models

/**
 * What a cash bubble shows under its lines for Send Back: returning a DM's opening payment to its
 * sender. Absent (null on the bubble) draws the bubble as before.
 *
 * Only the payment's receiver ever sees [Available] or [Sending]; its sender sees [SentBack] once
 * the return is in the transcript.
 */
enum class SendBackRow {
    /** The receiver can send the payment back. A tappable "Send Back" row. */
    Available,

    /** The return is being submitted. The row holds a spinner and takes no taps. */
    Sending,

    /** The payment has been returned. A "Sent Back" footer. */
    SentBack,
}
