package com.flipcash.app.core.route

import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.chat.ChatId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The group card's route is saved with the back stack, and an invite link's fragment is the chat
 * key's secret, so the link must reach the card some other way (spec group-invites.md surface 1).
 */
class GroupCardRouteTest {
    private val route = AppRoute.Menu.GroupCard(
        chatId = ChatId("aa"),
        title = "Moony",
        picture = null,
        memberCount = 48,
    )

    @Test
    fun `the saved route has no field a link could ride in`() {
        val saved = Json.encodeToString(AppRoute.Menu.GroupCard.serializer(), route)

        assertEquals(setOf("chatId", "title", "picture", "memberCount"), Json.parseToJsonElement(saved).jsonObject.keys)
    }

    @Test
    fun `the saved route holds no link`() {
        val saved = Json.encodeToString(AppRoute.serializer(), route)

        assertFalse("http" in saved)
        assertFalse("#s=" in saved)
        assertFalse("flipcash.com" in saved)
    }
}
