package xyz.flipchat.services.user

import com.bugsnag.android.Bugsnag
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.generator.OrganizerGenerator
import com.getcode.model.ID
import com.getcode.model.description
import com.getcode.model.uuid
import com.getcode.services.manager.MnemonicManager
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.FormatUtils
import com.mixpanel.android.mpmetrics.MixpanelAPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import xyz.flipchat.services.core.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unknown : AuthState
    data object Unregistered : AuthState
    data object LoggedInAwaitingUser : AuthState
    data object LoggedIn : AuthState
    data object LoggedOut : AuthState

    fun canOpenChatStream() = this is Unregistered || this is LoggedIn
}

@Singleton
class UserManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val organizerGenerator: OrganizerGenerator,
    private val mixpanelAPI: MixpanelAPI,
) {
    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    val entropy: String?
        get() = _state.value.entropy

    val keyPair: KeyPair?
        get() = _state.value.keyPair

    val userId: ID?
        get() = _state.value.userId

    val organizer: Organizer?
        get() = _state.value.organizer

    val displayName: String?
        get() = _state.value.displayName

    val userFlags: UserFlags?
        get() = _state.value.flags

    val openRoom: ID?
        get() = _state.value.openRoom

    val authState: AuthState
        get() = _state.value.authState

    data class State(
        val authState: AuthState = AuthState.Unknown,
        val entropy: String? = null,
        val keyPair: KeyPair? = null,
        val userId: ID? = null,
        val displayName: String? = null,
        val organizer: Organizer? = null,
        val flags: UserFlags? = null,
        val isTimelockUnlocked: Boolean = false,
        val openRoom: ID? = null,
    )

    fun establish(entropy: String) {
        val mnemonic = mnemonicManager.fromEntropyBase64(entropy)
        val authority = DerivedKey.derive(com.getcode.crypt.DerivePath.primary, mnemonic)
        val organizer = organizerGenerator.generate(mnemonic)
        _state.update {
            it.copy(
                entropy = entropy,
                keyPair = authority.keyPair,
                organizer = organizer
            )
        }
    }

    fun set(userId: ID) {
        _state.update {
            it.copy(userId = userId)
        }
        associate()
    }

    fun set(displayName: String) {
        _state.update {
            it.copy(
                displayName = displayName
            )
        }
        associate()
    }

    fun set(organizer: Organizer) {
        _state.update {
            it.copy(organizer = organizer)
        }
    }

    fun set(userFlags: UserFlags) {
        _state.update {
            it.copy(flags = userFlags)
        }
        associate()
    }

    fun set(authState: AuthState) {
        _state.update { it.copy(authState = authState) }
    }

    fun roomOpened(roomId: ID) {
        _state.update {
            it.copy(openRoom = roomId)
        }
    }

    fun roomClosed() {
        _state.update {
            it.copy(openRoom = null)
        }
    }

    fun didDetectUnlockedAccount() {
        _state.update {
            if (!it.isTimelockUnlocked) {
                it.copy(isTimelockUnlocked = true)
            } else {
                it
            }
        }
    }

    fun isSelf(id: ID?) = userId == id

    private fun associate() {
        if (!BuildConfig.DEBUG) {
            val distinctId = userId?.uuid?.toString()
            if (Bugsnag.isStarted()) {
                Bugsnag.setUser(distinctId, null, displayName)
                userFlags?.let { flags ->
                    Bugsnag.addMetadata(
                        /* section = */ "userflags",
                        /* value = */ mapOf(
                            "isStaff" to flags.isStaff,
                            "isRegistered" to flags.isRegistered,
                            "createCost" to FormatUtils.formatWholeRoundDown(flags.createCost.toKinValueDouble())
                        )
                    )
                }
            }

            mixpanelAPI.identify(distinctId)
        }
    }

    fun clear() {
        _state.update {
            it.copy(
                authState = AuthState.LoggedOut,
                entropy = null,
                keyPair = null,
                userId = emptyList(),
                organizer = null,
                flags = null,
                openRoom = null
            )
        }
    }

}