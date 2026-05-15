package com.flipcash.app.core.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadableTest {

    @Test
    fun `Loading state reports isLoading true`() {
        val state: Loadable<String> = Loadable.Loading()

        assertTrue(state.isLoading())
        assertFalse(state.isLoaded())
        assertFalse(state.isError())
    }

    @Test
    fun `Loaded state reports isLoaded true`() {
        val state: Loadable<String> = Loadable.Loaded("data")

        assertTrue(state.isLoaded())
        assertFalse(state.isLoading())
        assertFalse(state.isError())
    }

    @Test
    fun `Error state reports isError true`() {
        val state: Loadable<String> = Loadable.Error("something went wrong")

        assertTrue(state.isError())
        assertFalse(state.isLoading())
        assertFalse(state.isLoaded())
    }

    @Test
    fun `dataOrNull returns data for Loading with data`() {
        val state: Loadable<String> = Loadable.Loading(data = "partial")

        assertEquals("partial", state.dataOrNull)
    }

    @Test
    fun `dataOrNull returns null for Loading without data`() {
        val state: Loadable<String> = Loadable.Loading()

        assertNull(state.dataOrNull)
    }

    @Test
    fun `dataOrNull returns data for Loaded`() {
        val state: Loadable<String> = Loadable.Loaded("complete")

        assertEquals("complete", state.dataOrNull)
    }

    @Test
    fun `dataOrNull returns null for Error`() {
        val state: Loadable<String> = Loadable.Error("fail")

        assertNull(state.dataOrNull)
    }
}
