package com.flipcash.app.persistence

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Partial indexes for the three `chat_messages` scans that run at every login, before the chat list
 * paints: `failInterruptedSends`, `hasDuplicateClientIds` and `chatsWithKeyPending`. Each reads the
 * whole table to find rows that are almost never there, which took 1.9 to 2.6 s each on a
 * 1,000-chat, 10,000-message-per-chat database. Each index holds only the rows its scan wants, so
 * it stays near empty and costs a write only when a row enters or leaves that state.
 *
 * SQLite uses a partial index only for a query whose `WHERE` implies the index's, so each `WHERE`
 * here repeats its scan's condition exactly.
 *
 * Room declares the same names and columns on `ChatMessageEntity` without the `WHERE`, which it
 * can't express. Whatever Room creates from those declarations is a full index; [ensure] replaces
 * it, from [Migration52To53][FlipcashDatabase.Migration52To53] and from [Callback] on every open.
 */
object ChatPartialIndexes {
    const val SENDING = "index_chat_messages_status_sending"
    const val KEY_PENDING = "index_chat_messages_encryption_state_key_pending"
    const val CLIENT_ID = "index_chat_messages_chat_id_hex_pending_client_id_hex"

    internal val definitions: Map<String, String> = mapOf(
        SENDING to "CREATE INDEX `$SENDING` ON `chat_messages` (`status`) WHERE status = 'SENDING'",
        KEY_PENDING to
            "CREATE INDEX `$KEY_PENDING` ON `chat_messages` (`encryption_state`) WHERE encryption_state = 'KEY_PENDING'",
        CLIENT_ID to
            "CREATE INDEX `$CLIENT_ID` ON `chat_messages` (`chat_id_hex`, `pending_client_id_hex`) " +
                "WHERE pending_client_id_hex IS NOT NULL",
    )

    /** Creates each index that is missing or not partial; leaves the partial ones alone. */
    fun ensure(connection: SQLiteConnection) {
        val partial = mutableSetOf<String>()
        connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'chat_messages' " +
                "AND sql LIKE '% WHERE %'"
        ).use { statement ->
            while (statement.step()) partial += statement.getText(0)
        }
        for ((name, sql) in definitions) {
            if (name in partial) continue
            connection.execSQL("DROP INDEX IF EXISTS `$name`")
            connection.execSQL(sql)
        }
    }

    /** Runs [ensure] on every open: a fresh install, or a migration that rebuilt `chat_messages`. */
    object Callback : RoomDatabase.Callback() {
        override fun onOpen(connection: SQLiteConnection) = ensure(connection)
    }
}
