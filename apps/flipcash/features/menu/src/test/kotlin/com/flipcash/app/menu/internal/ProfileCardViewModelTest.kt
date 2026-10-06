package com.flipcash.app.menu.internal

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.share.TipCodeExporter
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.features.menu.R
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCardViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val exporter = mockk<TipCodeExporter>()
    private val resources = FakeResourceHelper()
        .stub(R.string.label_profileCardFileName, "Chat with %1\$s")
        .stub(R.string.label_profileCardFileNameFallback, "Profile Card")

    @Before
    @After
    fun clearAlerts() {
        BottomBarManager.clear()
    }

    /** Exports with [displayName] and returns the base name the exporter was asked to use. */
    private fun exportedBaseName(displayName: String): String? {
        val card = Scannable.TipCard(
            data = listOf<Byte>(1),
            user = UserProfile.Empty.copy(displayName = displayName),
        )
        val tipping = mockk<TippingCoordinator>()
        coEvery { tipping.resolveTipCard() } returns Result.success(card)
        val baseName = slot<String>()
        coEvery { exporter.export(any(), any(), capture(baseName), any()) } returns null

        val vm = ProfileCardViewModel(
            tippingCoordinator = tipping,
            tipCodeExporter = exporter,
            shareable = mockk<ShareSheetController>(relaxed = true),
            resources = resources,
        )
        vm.download()
        BottomBarManager.messages.value.first().actions.first().onClick()

        return if (baseName.isCaptured) baseName.captured else null
    }

    @Test
    fun `a plain name is used in the file name`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals("Chat with Ada", exportedBaseName("Ada"))
    }

    @Test
    fun `hostile characters are dropped from the name`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals("Chat with ab", exportedBaseName("a/b"))
    }

    @Test
    fun `a name of only hostile characters falls back to the generic name`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals("Profile Card", exportedBaseName("///"))
        BottomBarManager.clear()
        assertEquals("Profile Card", exportedBaseName("<>"))
    }

    @Test
    fun `a blank name falls back to the generic name`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals("Profile Card", exportedBaseName("   "))
    }
}
