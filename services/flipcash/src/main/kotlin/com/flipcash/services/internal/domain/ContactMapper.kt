package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.contact.v1.Model as ContactModel
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.network.extensions.toChatId
import com.flipcash.services.models.FlipcashContactEntry
import kotlin.time.Instant
import javax.inject.Inject

internal class ContactMapper @Inject constructor() :
    Mapper<ContactModel.FlipcashContact, FlipcashContactEntry> {
    override fun map(from: ContactModel.FlipcashContact): FlipcashContactEntry {
        return FlipcashContactEntry(
            phoneNumber = from.phone.value,
            dmChatId = from.dmChatId
                .takeIf { !it.value.isEmpty }
                ?.toChatId(),
            joinedAt = Instant.fromEpochSeconds(from.joinTs.seconds, from.joinTs.nanos),
        )
    }
}
