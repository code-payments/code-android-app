package com.flipcash.shared.chat.internal

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreApproveNewMembersStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = createTempDirectory("approve").toFile()
    private val owner = MutableStateFlow<String?>("account-a")
    private val store = DataStoreApproveNewMembersStore(
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "approve.preferences_pb") },
        owners = owner,
        currentOwner = { owner.value },
    )
    private val moony = ChatId("aa")
    private val other = ChatId("bb")

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `the switch is off until set`() = runBlocking {
        assertFalse(store.observe(moony).first())
    }

    @Test
    fun `the switch is kept per group`() = runBlocking {
        store.set(moony, true)

        assertTrue(store.observe(moony).first())
        assertFalse(store.observe(other).first())
    }

    @Test
    fun `turning it off again clears it for that group only`() = runBlocking {
        store.set(moony, true)
        store.set(other, true)
        store.set(moony, false)

        assertFalse(store.observe(moony).first())
        assertTrue(store.observe(other).first())
    }

    @Test
    fun `another account on the device has its own switch`() = runBlocking {
        store.set(moony, true)
        owner.value = "account-b"

        assertFalse(store.observe(moony).first())
    }

    @Test
    fun `signed out, nothing is on and nothing is kept`() = runBlocking {
        owner.value = null
        store.set(moony, true)
        owner.value = "account-a"

        assertFalse(store.observe(moony).first())
    }
}
