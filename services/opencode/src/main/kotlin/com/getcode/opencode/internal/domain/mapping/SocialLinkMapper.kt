package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.opencode.model.financial.SocialLink.*
import javax.inject.Inject

class SocialLinkMapper @Inject constructor(): Mapper<CurrencyService.SocialLink?, SocialLink?> {
    override fun map(from: CurrencyService.SocialLink?): SocialLink? {
        if (from == null) return null
        return when (from.typeCase) {
            CurrencyService.SocialLink.TypeCase.WEBSITE -> Website(from.website.url)
            CurrencyService.SocialLink.TypeCase.X -> X(from.x.username)
            CurrencyService.SocialLink.TypeCase.TELEGRAM -> Telegram(from.telegram.username)
            CurrencyService.SocialLink.TypeCase.DISCORD -> Discord(from.discord.inviteCode)

            CurrencyService.SocialLink.TypeCase.TYPE_NOT_SET -> null
        }
    }
}