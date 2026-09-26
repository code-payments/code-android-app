package com.flipcash.app.core.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionInfoTest {

    private val sha = "2acfecffeb1c1e7b8d64b0e3c6b7a1f2d9e0c4a5"

    @Test
    fun `the label is the first ten characters of the SHA`() {
        assertEquals("2acfecffeb", VersionInfo(commitSha = sha).commitLabel)
    }

    @Test
    fun `a dirty build appends an asterisk`() {
        assertEquals("2acfecffeb*", VersionInfo(commitSha = sha, isDirty = true).commitLabel)
    }

    @Test
    fun `a SHA shorter than ten characters is shown whole`() {
        assertEquals("2acfecf", VersionInfo(commitSha = "2acfecf").commitLabel)
    }

    @Test
    fun `surrounding whitespace is not counted toward the ten characters`() {
        assertEquals("2acfecffeb", VersionInfo(commitSha = " $sha\n").commitLabel)
    }

    @Test
    fun `an empty SHA has no label`() {
        assertNull(VersionInfo().commitLabel)
    }

    @Test
    fun `a blank SHA has no label, even when dirty`() {
        assertNull(VersionInfo(commitSha = "  ", isDirty = true).commitLabel)
    }
}
