package com.getcode.manager

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.getcode.App
import com.getcode.util.PhoneUtils
import com.getcode.view.main.invites.ContactModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LocalContactsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val phoneUtils: PhoneUtils,
) {
    fun query(): List<ContactModel> {
        val map = linkedMapOf<String, ContactModel>()

        val cr: ContentResolver = context.contentResolver
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val idIndex = cur.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                val id: String = cur.getString(idIndex)
                val name: String = cur.getString(nameIndex) ?: continue

                val initials = when {
                    name.length >= 3 && name.contains(" ") -> {
                        name
                            .split(" ")
                            .take(2)
                            .joinToString("") { it.take(1) }
                    }
                    name.isNotEmpty() -> name.take(1)
                    else -> ""
                }

                if (cur.getInt(hasPhoneIndex) > 0) {
                    cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(id),
                        null
                    )?.use { pCur ->
                        while (pCur.moveToNext()) {
                            val phone =
                                pCur.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                                ).let {
                                    pCur.getString(it)
                                } ?: pCur.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER
                                ).let {
                                    pCur.getString(it)
                                }.let {
                                    if (!it.startsWith("+")) "+$it" else it
                                }

                            val phoneNumber: String = phone
                                .replace("(", "")
                                .replace(")", "")
                                .replace("-", "")
                                .replace(" ", "")

                            val phoneNumberFormatted = phoneUtils.formatNumber(
                                phoneNumber
                            )

                            if (phoneNumber.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                                map[phoneNumber] = ContactModel(
                                    id = id,
                                    name = name,
                                    phoneNumber = phoneNumber,
                                    phoneNumberFormatted = phoneNumberFormatted,
                                    initials = initials,
                                    isInvited = false
                                )
                            }
                        }
                    } ?: return emptyList()
                }
            }
        }

        return map.values.toList()
    }
}