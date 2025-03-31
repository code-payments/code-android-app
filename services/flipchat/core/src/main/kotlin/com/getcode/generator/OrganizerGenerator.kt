package com.getcode.generator

import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.organizer.Organizer
import javax.inject.Inject

class OrganizerGenerator @Inject constructor(): Generator<MnemonicPhrase, Organizer> {

    override fun generate(predicate: MnemonicPhrase): Organizer {
        return Organizer.newInstance(predicate)
    }
}