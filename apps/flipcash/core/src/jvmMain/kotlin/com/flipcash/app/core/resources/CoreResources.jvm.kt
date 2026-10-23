package com.flipcash.app.core.resources

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.ic_checked_green
import com.flipcash.core.resources.ic_nav_tipcard
import com.flipcash.core.resources.label_fromAppreciation
import com.flipcash.core.resources.label_fromCurrencyAppreciation
import com.flipcash.core.resources.label_fromCurrencyDepreciation
import com.flipcash.core.resources.label_fromDepreciation
import com.flipcash.core.resources.subtitle_addMoney
import com.flipcash.core.resources.subtitle_scanTipCard
import com.flipcash.core.resources.title_addMoney
import com.flipcash.core.resources.title_scanTipCard
import com.flipcash.core.resources.title_recentActivity
import com.flipcash.core.resources.label_chatReceipt_yesterday
import com.getcode.util.resources.ComposeResourceIds

@Suppress("PropertyName")
actual object CoreStrings {
    actual val title_addMoney: Int = ComposeResourceIds.register(Res.string.title_addMoney)
    actual val subtitle_addMoney: Int = ComposeResourceIds.register(Res.string.subtitle_addMoney)
    actual val title_scanTipCard: Int = ComposeResourceIds.register(Res.string.title_scanTipCard)
    actual val subtitle_scanTipCard: Int = ComposeResourceIds.register(Res.string.subtitle_scanTipCard)
    actual val label_fromAppreciation: Int = ComposeResourceIds.register(Res.string.label_fromAppreciation)
    actual val label_fromDepreciation: Int = ComposeResourceIds.register(Res.string.label_fromDepreciation)
    actual val label_fromCurrencyAppreciation: Int = ComposeResourceIds.register(Res.string.label_fromCurrencyAppreciation)
    actual val label_fromCurrencyDepreciation: Int = ComposeResourceIds.register(Res.string.label_fromCurrencyDepreciation)
    actual val title_recentActivity: Int = ComposeResourceIds.register(Res.string.title_recentActivity)
    actual val label_chatReceipt_yesterday: Int = ComposeResourceIds.register(Res.string.label_chatReceipt_yesterday)
}

@Suppress("PropertyName")
actual object CoreDrawables {
    actual val ic_nav_tipcard: Int = ComposeResourceIds.register(Res.drawable.ic_nav_tipcard)
    actual val ic_checked_green: Int = ComposeResourceIds.register(Res.drawable.ic_checked_green)
    actual val ic_chevron_right: Int = checkNotNull(ComposeResourceIds.drawableId("ic_chevron_right"))
}
