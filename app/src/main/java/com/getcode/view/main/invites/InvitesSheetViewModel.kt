package com.getcode.view.main.invites

import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.codeinc.gen.invite.v2.InviteService
import com.getcode.R
import com.getcode.db.Database
import com.getcode.manager.LocalContactsManager
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.network.repository.ContactsRepository
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.InviteRepository
import com.getcode.network.repository.replaceParam
import com.getcode.util.IntentUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.makeE164
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class ContactModel(
    val id: String = "",
    val name: String,
    val phoneNumber: String = "",
    val phoneNumberFormatted: String = "",
    val initials: String = "",
    val isInvited: Boolean = false,
    val isRegistered: Boolean = false,
)

data class InvitesSheetUiModel(
    val isContactsPermissionGranted: Boolean? = null,
    val isPermissionRequested: Boolean = false,
    val inviteCount: Int = 0,
    val contacts: List<ContactModel> = listOf(),
    val contactsFiltered: List<ContactModel> = listOf(),
    val contactFilterString: String = "",
    val contactsLoading: Boolean = true,
)

@HiltViewModel
class InvitesSheetViewModel @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val contactsRepository: ContactsRepository,
    private val localContactsManager: LocalContactsManager,
    private val identityRepository: IdentityRepository,
    private val resources: ResourceHelper,
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(InvitesSheetUiModel())

    fun init() {
        Database.isInit
            .flatMap { inviteRepository.getInviteCount() }
            .subscribeOn(Schedulers.computation())
            .subscribe {
                uiFlow.update { v ->
                    v.copy(inviteCount = it.toInt())
                }
            }
    }

    private fun initContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            val keyPair = SessionManager.getKeyPair() ?: return@launch

            val contacts = localContactsManager.query()
            if (contacts.isEmpty()) {
                updateContacts(contacts)
                return@launch
            }

            identityRepository.getUserLocal()
                .flatMap { res ->
                    contactsRepository.uploadContacts(
                        keyPair,
                        res.dataContainerId.toByteArray(),
                        contacts.map { c -> c.phoneNumber })
                        .map { res }
                }
                .flatMap {
                    contactsRepository.getContacts(
                        keyPair,
                        it.dataContainerId.toByteArray()
                    )
                }
                .subscribeOn(Schedulers.computation())
                .subscribe(
                    { remoteContacts -> displayContacts(contacts, remoteContacts) },
                    { displayContacts(contacts) }
                )
        }
    }

    private fun displayContacts(
        contacts_: List<ContactModel>,
        remoteContacts: List<ContactsRepository.GetContactsResponse> = listOf()
    ) {
        val remoteContactsMap =
            remoteContacts.associateBy { it.phoneNumber.makeE164() }
        val contacts = contacts_.map { contact ->
            val phoneInternational = contact.phoneNumber.makeE164()
            contact.copy(
                isInvited = remoteContactsMap[phoneInternational]?.isInvited == true,
                isRegistered = remoteContactsMap[phoneInternational]?.isRegistered == true,
            )
        }.let { list ->
            list.sortedWith(
                compareBy<ContactModel> { !it.isRegistered }.thenBy { !it.isInvited }
                    .thenBy { it.name }
            )
        }
        updateContacts(contacts)
    }

    private fun updateContacts(contacts: List<ContactModel>) {
        uiFlow.update {
            it.copy(
                contacts = contacts,
                contactsFiltered = contacts,
                contactsLoading = false
            )
        }
    }

    fun inviteContactCustomInput(phoneValue: String) {
        val phoneE164 = phoneValue.makeE164(java.util.Locale.getDefault())

        if (phoneE164.length < 8) {
            TopBarManager.showMessage(
                resources.getString(
                    R.string.error_title_invalidInvitePhone),
                resources.getString(
                    R.string.error_description_invalidInvitePhone)
            )
        } else {
            inviteContact(phoneE164)
        }
    }

    fun inviteContact(phoneValue: String) {
        inviteRepository.whitelist(phoneValue)
            .subscribe {
                if (it == InviteService.InvitePhoneNumberResponse.Result.INVITE_COUNT_EXCEEDED) {
                    TopBarManager.showMessage(
                        resources.getString(
                            R.string.error_title_noInvitesLeft),
                        resources.getString(
                            R.string.error_description_noInvitesLeft)
                    )
                } else {
                    IntentUtils.launchSmsIntent(
                        phoneValue,
                        getString(R.string.subtitle_inviteText).replaceParam("getcode.com/download")
                    )
                    val contacts = uiFlow.value.contacts.toMutableList()
                    val index = contacts.indexOfFirst { i -> i.phoneNumber == phoneValue }
                    if (index >= 0) {
                        contacts[index] = contacts[index].copy(isInvited = true)
                        updateContacts(contacts)
                    }
                }
            }
    }

    fun onContactsPermissionChanged(isGranted: Boolean) {
        if (isGranted && uiFlow.value.isContactsPermissionGranted == isGranted) return
        uiFlow.update {
            it.copy(isContactsPermissionGranted = isGranted)
        }

        if (isGranted) {
            initContacts()
        } else if (uiFlow.value.isPermissionRequested) {
            TopBarManager.showMessage(
                TopBarManager.TopBarMessage(
                    title = "Failed to access contacts",
                    message = "Please allow Code access to Contacts in Settings.",
                    type = TopBarManager.TopBarMessageType.ERROR,
                    secondaryText = resources.getString(R.string.action_openSettings),
                    secondaryAction = { IntentUtils.launchAppSettings() }
                )
            )
        }
    }

    fun onContactsPermissionRequested() {
        uiFlow.update {
            it.copy(isPermissionRequested = true)
        }
    }

    fun updateContactFilterString(contactFilterString: String) {
        val contacts = (uiFlow.value.contacts)
        val contactsFiltered =
            if (contactFilterString.isBlank()) {
                contacts
            } else {
                (uiFlow.value.contacts)
                    .filter {
                        val filterString = contactFilterString.toLowerCase(Locale.current)
                        it.name.lowercase().contains(filterString.lowercase()) ||
                                it.phoneNumber.contains(filterString)
                    }
            }

        uiFlow.update {
            it.copy(
                contactFilterString = contactFilterString,
                contactsFiltered = contactsFiltered
            )
        }
    }
}
