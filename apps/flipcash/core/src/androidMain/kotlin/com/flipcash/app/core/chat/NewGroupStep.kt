package com.flipcash.app.core.chat

import android.os.Parcelable
import com.getcode.navigation.Sheet
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * The steps of creating a public group — section 10153:22901.
 *
 * A flow rather than three pushed routes because all three read and write one draft: the title, the
 * picture, the mint and the amount are entered on [Form], and the mint and the amount are changed
 * on [SelectCurrency] and [CustomAmount]. A shared view model
 * ([com.getcode.navigation.flow.flowSharedViewModel]) holds that draft for the flow's lifetime,
 * which is also what keeps one create attempt's idempotency key alive across a retry.
 *
 * Creating the chat ends the flow. The invite link is handed out from inside the group
 * ([ChatStep.InviteToGroup]), so it is not a step here.
 */
@Serializable
sealed interface NewGroupStep : FlowStep, Parcelable {

    /** Node 10127:118014 — name, picture, and the balance rule. */
    @Parcelize
    @Serializable
    data object Form : NewGroupStep

    /**
     * Node 10127:118100 — which mint the balance requirement is denominated in.
     *
     * A [Sheet] so the form stays on screen behind it: the picker is a change to one field of the
     * draft, and dismissing it leaves the rest of the form exactly as it was.
     */
    @Parcelize
    @Serializable
    data object SelectCurrency : NewGroupStep, Sheet

    /**
     * The `…` preset — the balance requirement as a free amount rather than one of the three chips.
     *
     * The design names the chip but not where it lands, so this is the app's standard amount entry
     * ([com.flipcash.shared.amountentry.AmountEntryScreen]), the same keypad the minimum-to-chat fee
     * is set on. A [Sheet] for the same reason as [SelectCurrency]: it edits one field of the draft
     * the form is still holding.
     */
    @Parcelize
    @Serializable
    data object CustomAmount : NewGroupStep, Sheet
}
