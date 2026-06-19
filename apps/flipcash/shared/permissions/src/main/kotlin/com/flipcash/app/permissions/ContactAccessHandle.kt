package com.flipcash.app.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsPickerSessionContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.getcode.util.permissions.PermissionHandle
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberContactPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedContact(
    val phoneNumber: String,
    val displayName: String,
    val photoUri: String? = null,
)

sealed interface ContactAccessResult {
    data object Granted : ContactAccessResult
    data class Picked(val contacts: List<PickedContact>) : ContactAccessResult
    data object Denied : ContactAccessResult
    data object PermanentlyDenied : ContactAccessResult
    data object Canceled : ContactAccessResult
}

@Stable
class ContactAccessHandle(
    val launch: () -> Unit,
    private val permissionHandle: PermissionHandle? = null,
) {
    val permissionStatus: PermissionResult
        get() = permissionHandle?.status ?: PermissionResult.NotRequested
}

fun PermissionHandle.asContactAccessHandle() = ContactAccessHandle(
    launch = ::launch,
    permissionHandle = this,
)

@Composable
fun rememberContactAccessHandle(
    isPickerMode: Boolean,
    onResult: (ContactAccessResult) -> Unit = { },
): ContactAccessHandle {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentIsPickerMode by rememberUpdatedState(isPickerMode)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supportsMultiPick = Build.VERSION.SDK_INT >= 37

    val multiPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val sessionUri = result.data?.data
            if (sessionUri != null) {
                scope.launch {
                    val contacts = readContactsFromSessionUri(sessionUri, context)
                    if (contacts.isNotEmpty()) {
                        currentOnResult(ContactAccessResult.Picked(contacts))
                    } else {
                        currentOnResult(ContactAccessResult.Canceled)
                    }
                }
            } else {
                currentOnResult(ContactAccessResult.Canceled)
            }
        } else {
            currentOnResult(ContactAccessResult.Canceled)
        }
    }

    val permissionHandle = rememberContactPermission { result ->
        val mapped = when (result) {
            PermissionResult.Granted -> ContactAccessResult.Granted
            PermissionResult.Denied -> ContactAccessResult.Denied
            PermissionResult.PermanentlyDenied -> ContactAccessResult.PermanentlyDenied
            PermissionResult.NotRequested -> null
        }
        if (mapped != null) currentOnResult(mapped)
    }

    return remember {
        ContactAccessHandle(
            launch = {
                if (currentIsPickerMode && supportsMultiPick) {
                    multiPickLauncher.launch(createMultiPickIntent())
                } else {
                    permissionHandle.launch()
                }
            },
            permissionHandle = permissionHandle,
        )
    }
}

@RequiresApi(37)
private fun createMultiPickIntent(): Intent =
    Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        putStringArrayListExtra(
            ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
            arrayListOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
        )
    }

private suspend fun readContactsFromSessionUri(
    sessionUri: Uri,
    context: Context,
): List<PickedContact> = withContext(Dispatchers.IO) {
    val contacts = mutableMapOf<String, PickedContact>()
    val projection = arrayOf(
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.DATA1,
    )

    context.contentResolver.query(sessionUri, projection, null, null, null)?.use { cursor ->
        val lookupKeyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
        val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)

        while (cursor.moveToNext()) {
            val lookupKey = cursor.getString(lookupKeyIdx) ?: continue
            val name = cursor.getString(nameIdx) ?: ""
            val mimeType = cursor.getString(mimeTypeIdx) ?: continue
            val data1 = cursor.getString(data1Idx) ?: continue

            if (mimeType == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                if (lookupKey !in contacts) {
                    contacts[lookupKey] = PickedContact(
                        phoneNumber = data1,
                        displayName = name,
                    )
                }
            }
        }
    }

    contacts.values.toList()
}
