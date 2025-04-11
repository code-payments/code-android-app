package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.account.v1.AccountService
import com.flipcash.services.internal.model.account.UserFlags
import com.getcode.opencode.internal.domain.mapper.Mapper
import javax.inject.Inject

internal class UserFlagsMapper @Inject constructor(): Mapper<AccountService.UserFlags, UserFlags> {
    override fun map(from: AccountService.UserFlags): UserFlags {
        return UserFlags(
            isRegistered = from.isRegisteredAccount,
            isStaff = from.isStaff
        )
    }
}