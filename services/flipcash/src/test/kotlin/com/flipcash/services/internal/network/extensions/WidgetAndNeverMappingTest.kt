package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.blocksReactions
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetAndNeverMappingTest {

    @Test
    fun `share profile widget maps to the domain widget`() {
        val content = MessagingModel.Content.newBuilder()
            .setWidget(
                MessagingModel.WidgetContent.newBuilder().setShareProfile(
                    MessagingModel.ShareProfileWidget.newBuilder()
                        .setUsername(Common.Username.newBuilder().setValue("alice"))
                )
            )
            .build()

        assertEquals(
            MessageContent.Widget(WidgetContent.ShareProfile("alice")),
            content.toMessageContent(),
        )
    }

    @Test
    fun `widget with no recognised variant maps to unsupported`() {
        val content = MessagingModel.Content.newBuilder()
            .setWidget(MessagingModel.WidgetContent.getDefaultInstance())
            .build()

        assertEquals(MessageContent.Widget(WidgetContent.Unsupported), content.toMessageContent())
    }

    @Test
    fun `a widget can't be built for sending`() {
        assertFailsWith<IllegalStateException> {
            MessageContent.Widget(WidgetContent.ShareProfile("alice")).asContent()
        }
    }

    @Test
    fun `speaker never maps to Never and back`() {
        val proto = ChatRuleRequirement.Never.asProtoSpeakerRules()

        assertEquals(ChatModel.SpeakerRules.KindCase.NEVER, proto.kindCase)
        assertEquals(ChatRuleRequirement.Never, proto.toRuleRequirementOrNull())
    }

    @Test
    fun `speaker creator maps to Creator and back`() {
        val proto = ChatRuleRequirement.Creator.asProtoSpeakerRules()

        assertEquals(ChatModel.SpeakerRules.KindCase.CREATOR, proto.kindCase)
        assertEquals(ChatRuleRequirement.Creator, proto.toRuleRequirementOrNull())
    }

    @Test
    fun `an unset speaker rule is kept as unsupported, not dropped`() {
        val rules = ChatModel.Rules.newBuilder()
            .addSpeaker(ChatModel.SpeakerRules.getDefaultInstance())
            .addSpeaker(ChatModel.SpeakerRules.newBuilder().setStaff(ChatModel.StaffRequirement.getDefaultInstance()))
            .build()

        assertEquals(
            listOf(ChatRuleRequirement.UnsupportedSpeakerRule, ChatRuleRequirement.Staff),
            rules.toChatRules().speaker,
        )
    }

    @Test
    fun `unsupported speaker rule has no wire form`() {
        assertFailsWith<IllegalStateException> { ChatRuleRequirement.UnsupportedSpeakerRule.asProtoSpeakerRules() }
        assertFailsWith<IllegalStateException> { ChatRuleRequirement.UnsupportedSpeakerRule.asProtoListenerRules() }
    }

    @Test
    fun `creator and unsupported block posting but not reactions`() {
        assertFalse(ChatRuleRequirement.Creator.blocksReactions)
        assertFalse(ChatRuleRequirement.UnsupportedSpeakerRule.blocksReactions)
        assertTrue(ChatRuleRequirement.Never.blocksReactions)
        assertTrue(ChatRuleRequirement.Staff.blocksReactions)
    }

    @Test
    fun `creator is speaker-only and is rejected as a listener rule`() {
        assertFailsWith<IllegalStateException> { ChatRuleRequirement.Creator.asProtoListenerRules() }
    }
}
