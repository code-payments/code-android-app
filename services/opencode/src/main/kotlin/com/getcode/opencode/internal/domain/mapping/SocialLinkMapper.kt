package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.opencode.model.financial.SocialLink.*
import javax.inject.Inject

class SocialLinkMapper @Inject constructor(): Mapper<OcpCurrencyService.SocialLink?, SocialLink?> {
    override fun map(from: OcpCurrencyService.SocialLink?): SocialLink? {
        if (from == null) return null
        return when (from.typeCase) {
            OcpCurrencyService.SocialLink.TypeCase.WEBSITE -> Website(from.website.url)
            OcpCurrencyService.SocialLink.TypeCase.X -> X(from.x.username)
            OcpCurrencyService.SocialLink.TypeCase.TELEGRAM -> Telegram(from.telegram.username)
            OcpCurrencyService.SocialLink.TypeCase.DISCORD -> Discord(from.discord.inviteCode)

            OcpCurrencyService.SocialLink.TypeCase.TYPE_NOT_SET -> null
        }
    }
}