package com.flipcash.shared.payments

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.Flow

/**
 * The amount another user must send to open a DM with [recipient], as shown on profiles. A
 * [recipient] who set no fee, or whose fee has no rate to convert by, falls back to the regional
 * minimum; null only until that minimum has resolved. The You tab, Edit Profile and the other-user
 * profile all show this value, so it has one name.
 */
fun TipPaymentDelegate.startChattingFee(recipient: UserProfile?): Flow<Fiat?> =
    minimumToOpenDmWith(recipient)
