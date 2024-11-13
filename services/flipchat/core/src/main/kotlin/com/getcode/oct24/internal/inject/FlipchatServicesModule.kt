package com.getcode.oct24.internal.inject

import com.getcode.oct24.user.UserManager
import com.getcode.services.annotations.EcdsaLookup
import com.getcode.services.model.EcdsaTuple
import com.getcode.services.model.EcdsaTupleQuery
import com.getcode.services.network.core.NetworkOracle
import com.getcode.services.network.core.NetworkOracleImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object FlipchatServicesModule {

    @Provides
    @EcdsaLookup
    fun providesEcdsaTuple(
        userManager: UserManager
    ): EcdsaTupleQuery {
        return {
            EcdsaTuple(userManager.keyPair, userManager.userId)
        }
    }

    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

}