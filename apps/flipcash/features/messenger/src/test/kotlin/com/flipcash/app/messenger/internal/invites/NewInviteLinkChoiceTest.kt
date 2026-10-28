package com.flipcash.app.messenger.internal.invites

import android.os.Parcel
import com.flipcash.app.core.chat.NewInviteLinkChoice
import com.flipcash.shared.chat.InviteExpiry
import com.flipcash.shared.chat.InviteSettings
import com.flipcash.shared.chat.InviteUses
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** New Invite Link returns its pick as a [NewInviteLinkChoice], so every preset must survive a Parcel and map back. */
@RunWith(RobolectricTestRunner::class)
class NewInviteLinkChoiceTest {

    @Test
    fun `every expiry and use limit round-trips through a parcel and back to settings`() {
        for (expiry in InviteExpiry.entries) {
            for (uses in InviteUses.entries) {
                val settings = InviteSettings(expiry, uses)
                val parcel = Parcel.obtain()
                try {
                    parcel.writeParcelable(settings.toChoice(), 0)
                    parcel.setDataPosition(0)
                    val read = parcel.readParcelable<NewInviteLinkChoice>(NewInviteLinkChoice::class.java.classLoader)
                    assertEquals(settings, read!!.toSettings())
                } finally {
                    parcel.recycle()
                }
            }
        }
    }
}
