package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import org.junit.Test
import kotlin.test.assertEquals

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
    fun `share profile widget round trips through the wire type`() {
        val domain = MessageContent.Widget(WidgetContent.ShareProfile("alice"))

        assertEquals(domain, domain.asContent().toMessageContent())
    }

    @Test
    fun `speaker never maps to Never and back`() {
        val proto = ChatRuleRequirement.Never.asProtoSpeakerRules()

        assertEquals(ChatModel.SpeakerRules.KindCase.NEVER, proto.kindCase)
        assertEquals(ChatRuleRequirement.Never, proto.toRuleRequirementOrNull())
    }
}
