package com.getcode.model


import com.codeinc.gen.transaction.v2.TransactionService

enum class AirdropType {
    Unknown,
    GiveFirstKin,
    GetFirstKin;

    companion object {
        fun getInstance(airdropType: TransactionService.AirdropType): AirdropType? {
            return when (airdropType) {
                TransactionService.AirdropType.UNKNOWN -> Unknown
                TransactionService.AirdropType.GIVE_FIRST_KIN -> GiveFirstKin
                TransactionService.AirdropType.GET_FIRST_KIN -> GetFirstKin
                TransactionService.AirdropType.UNRECOGNIZED -> null
            }
        }
    }
}