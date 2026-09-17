package com.flipcash.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `MessagingStyle` renders the sender itself, so a body that names its own sender shows the name
 * twice — which is what the server sends, since a plain notification has nowhere else to put it.
 */
class MessageBodyPlannerTest {

    @Test
    fun `the sender's own name is dropped from the front of their message`() {
        assertEquals("whoa", planMessageBody("Kevin Ricoy: whoa", listOf("Kevin Ricoy")))
    }

    @Test
    fun `a name the sender is known by elsewhere is dropped too`() {
        // Rendered under the address-book name; composed by the server from the profile's.
        assertEquals(
            "Howdy",
            planMessageBody("neilbryan: Howdy", listOf("Neil", "neilbryan")),
        )
    }

    @Test
    fun `the longest matching name is what gets dropped`() {
        assertEquals("hi", planMessageBody("Ted Livingston: hi", listOf("Ted", "Ted Livingston")))
    }

    @Test
    fun `someone else's name is left alone`() {
        assertEquals("Neil: Howdy", planMessageBody("Neil: Howdy", listOf("Kevin Ricoy")))
    }

    @Test
    fun `a message that is only the sender's name survives`() {
        assertEquals("Kevin Ricoy:", planMessageBody("Kevin Ricoy:", listOf("Kevin Ricoy")))
    }

    @Test
    fun `a body with no sender to match is unchanged`() {
        assertEquals("whoa", planMessageBody("whoa", listOf(null, "", "   ")))
    }

    @Test
    fun `a push with no body plans an empty message`() {
        assertEquals("", planMessageBody(null, listOf("Kevin Ricoy")))
    }
}
