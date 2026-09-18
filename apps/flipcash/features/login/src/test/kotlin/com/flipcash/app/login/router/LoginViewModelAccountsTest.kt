package com.flipcash.app.login.router

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The router asks two separate questions of this state: has the stored-account read come back, and
 * did it find anything. Collapsing them is the cold-start bug — `hasStoredAccounts` is false before
 * the read lands as well as after an empty one, so only `storedAccountsChecked` says which.
 */
class LoginViewModelAccountsTest {

    @Test
    fun `the initial state is not yet an answer`() {
        val state = LoginViewModel.State()

        assertFalse(state.storedAccountsChecked)
        assertFalse(state.hasStoredAccounts)
    }

    @Test
    fun `records that the store has accounts`() {
        val state = LoginViewModel.updateStateForEvent(
            LoginViewModel.Event.OnStoredAccountsChanged(hasAccounts = true)
        )(LoginViewModel.State())

        assertTrue(state.hasStoredAccounts)
        assertTrue(state.storedAccountsChecked)
    }

    @Test
    fun `an empty store is still an answer`() {
        val state = LoginViewModel.updateStateForEvent(
            LoginViewModel.Event.OnStoredAccountsChanged(hasAccounts = false)
        )(LoginViewModel.State())

        // Distinguishes an empty store from a read that has not come back. A reducer that derived
        // `storedAccountsChecked` from `hasAccounts` would pass every other test here and still
        // leave Log In waiting forever on someone with no stored accounts.
        assertTrue(state.storedAccountsChecked)
        assertFalse(state.hasStoredAccounts)
    }
}
