package com.getcode.ui.tips

import com.getcode.ui.tips.definitions.DownloadCodeTip
import dev.bmcreations.tipkit.engines.TipInterface
import javax.inject.Inject

class DefinedTips @Inject constructor(
    val downloadCodeTip: DownloadCodeTip
): TipInterface


