package com.flipcash.app.tokens

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionNotificationPromptTest {

    private val dir: File = Files.createTempDirectory("prompt-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val marker = File(dir, "initialized")

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "prompt.preferences_pb") })

    private fun store(ds: DataStore<Preferences> = dataStore()) = DataStoreShownStore(ds, marker)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `request is held as state until consumed`() {
        val prompt = TransactionNotificationPrompt(store())
        assertFalse(prompt.pending.value)

        // No collector yet: the request is not lost.
        prompt.request()
        assertTrue(prompt.pending.value)

        prompt.consume()
        assertFalse(prompt.pending.value)
    }

    @Test
    fun `shown is persisted and survives a new instance`() = runTest {
        val ds = dataStore()
        val prompt = TransactionNotificationPrompt(store(ds))
        assertFalse(prompt.hasBeenShown())

        prompt.markShown()
        assertTrue(prompt.hasBeenShown())

        assertTrue(TransactionNotificationPrompt(store(ds)).hasBeenShown())
    }

    @Test
    fun `restored shown flag is cleared on a fresh install`() = runTest {
        val ds = dataStore()
        // Simulates a backup restore: preferences exist but the noBackupFilesDir marker does not.
        marker.createNewFile()
        TransactionNotificationPrompt(store(ds)).markShown()
        marker.delete()

        val restored = TransactionNotificationPrompt(store(ds))
        assertFalse(restored.hasBeenShown())
        assertTrue(marker.exists())

        restored.markShown()
        assertEquals(true, TransactionNotificationPrompt(store(ds)).hasBeenShown())
    }
}
