package com.getcode.generator

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.organizer.GiftCardAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GiftCardGenerator @Inject constructor(
    @ApplicationContext private val context: Context
): Generator<MnemonicPhrase?, GiftCardAccount> {
    override fun generate(predicate: MnemonicPhrase?): GiftCardAccount {
        return GiftCardAccount.newInstance(context, predicate)
    }
}