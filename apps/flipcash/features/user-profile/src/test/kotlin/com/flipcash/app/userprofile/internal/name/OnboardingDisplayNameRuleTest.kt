package com.flipcash.app.userprofile.internal.name

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.core.DisplayNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingDisplayNameRuleTest {

    @Test
    fun `accepts ASCII letters digits and spaces`() {
        assertTrue(isValidOnboardingDisplayName("Taylor Smith 2"))
        assertTrue(isValidOnboardingDisplayName("Taylor  Smith"))
    }

    @Test
    fun `trims surrounding whitespace before validating`() {
        assertTrue(isValidOnboardingDisplayName("  Taylor "))
        assertTrue(isValidOnboardingDisplayName("\tTaylor\n"))
    }

    @Test
    fun `rejects empty and whitespace only`() {
        assertFalse(isValidOnboardingDisplayName(""))
        assertFalse(isValidOnboardingDisplayName("   "))
    }

    @Test
    fun `rejects accents punctuation emoji and non-space whitespace`() {
        assertFalse(isValidOnboardingDisplayName("José"))
        assertFalse(isValidOnboardingDisplayName("Taylor!"))
        assertFalse(isValidOnboardingDisplayName("😀"))
        assertFalse(isValidOnboardingDisplayName("Tay\tlor"))
        assertFalse(isValidOnboardingDisplayName("Tay\nlor"))
        assertFalse(isValidOnboardingDisplayName("Tay lor"))
    }

    @Test
    fun `length limit is 64 after trimming`() {
        assertTrue(isValidOnboardingDisplayName("a".repeat(64)))
        assertTrue(isValidOnboardingDisplayName(" " + "a".repeat(64) + " "))
        assertFalse(isValidOnboardingDisplayName("a".repeat(65)))
    }

    @Test
    fun `only onboarding applies the ASCII rule`() {
        val state = NameEntryViewModel.State()
        state.nameFieldState.setTextAndPlaceCursorAtEnd("José")

        assertFalse(state.isSubmittable(DisplayNameSource.Onboarding))
        assertTrue(state.isSubmittable(DisplayNameSource.MyAccount))
        assertTrue(state.isSubmittable(DisplayNameSource.TipCardSetup))
        assertEquals(true, state.hasName)
    }

    @Test
    fun `trims the same set as iOS whitespacesAndNewlines`() {
        assertTrue(isValidOnboardingDisplayName("Taylor\u00A0"))
        assertTrue(isValidOnboardingDisplayName("\u3000Taylor"))
        assertTrue(isValidOnboardingDisplayName("Taylor\u0085"))
        assertFalse(isValidOnboardingDisplayName("Taylor\u001F"))
        assertFalse(isValidOnboardingDisplayName("Tay\u00A0lor"))
    }

    @Test
    fun `onboarding submits the trimmed name and other sources the typed text`() {
        val typed = "\u3000Taylor Smith\u0085"
        assertEquals("Taylor Smith", NameEntryViewModel.submittedName(typed, DisplayNameSource.Onboarding))
        assertEquals(typed, NameEntryViewModel.submittedName(typed, DisplayNameSource.MyAccount))
        assertEquals(typed, NameEntryViewModel.submittedName(typed, DisplayNameSource.TipCardSetup))
    }
}
