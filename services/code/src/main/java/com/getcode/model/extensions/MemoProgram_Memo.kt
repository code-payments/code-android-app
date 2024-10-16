package com.getcode.model.extensions

import com.getcode.model.SocialUser
import com.getcode.solana.instructions.programs.MemoProgram_Memo

fun MemoProgram_Memo.Companion.newInstance(tipMetadata: SocialUser): MemoProgram_Memo {
    val memo = "tip:${tipMetadata.platform}:${tipMetadata.username}"

    return MemoProgram_Memo(
        memo.toByteArray().toList()
    )
}
