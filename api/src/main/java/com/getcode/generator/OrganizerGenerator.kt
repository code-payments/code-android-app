package com.getcode.generator

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.organizer.Organizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class OrganizerGenerator @Inject constructor(
    @ApplicationContext private val context: Context
): Generator<MnemonicPhrase, Organizer> {

    override fun generate(predicate: MnemonicPhrase): Organizer {
        return Organizer.newInstance(context, predicate)
    }
}