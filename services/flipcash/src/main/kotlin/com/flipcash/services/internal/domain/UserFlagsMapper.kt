package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService
import com.flipcash.services.internal.model.account.UserFlags
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

internal class UserFlagsMapper @Inject constructor():
    Mapper<FlipcashAccountService.UserFlags, UserFlags> {
    override fun map(from: FlipcashAccountService.UserFlags): UserFlags {
        return UserFlags(
            isRegistered = from.isRegisteredAccount,
            isStaff = from.isStaff
        )
    }
}