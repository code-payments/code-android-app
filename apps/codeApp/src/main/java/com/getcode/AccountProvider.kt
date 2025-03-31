package com.getcode

import android.accounts.AccountManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.getcode.util.AccountUtils
import kotlinx.coroutines.runBlocking

class AccountProvider : ContentProvider() {

    override fun onCreate(): Boolean {

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val token = runBlocking { AccountUtils.getToken(context) }
        val cursor = MatrixCursor(arrayOf(AccountManager.KEY_AUTHTOKEN))
        cursor.addRow(arrayOf(token))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Handle insertion of new data if needed
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        // Handle deletion of data if needed
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        // Handle updating of data if needed
        return 0
    }

    override fun getType(uri: Uri): String {
        // Return the MIME type of data based on the URI pattern
        return "vnd.android.cursor.dir/vnd.getcode.account"
    }
}