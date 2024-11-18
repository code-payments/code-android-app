package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.account.v1.AccountService
import com.getcode.model.Kin
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.user.UserFlags
import xyz.flipchat.services.internal.network.extensions.toPublicKey
import javax.inject.Inject

class UserFlagsMapper @Inject constructor(): Mapper<AccountService.UserFlags, UserFlags> {
    override fun map(from: AccountService.UserFlags): UserFlags {
        return UserFlags(
            isStaff = from.isStaff,
            createCost = Kin.fromQuarks(from.startGroupFee.quarks.ifZeroOrElse(200) { it / 100_000 }),
            feeDestination = from.feeDestination.toPublicKey()
        )
    }
}