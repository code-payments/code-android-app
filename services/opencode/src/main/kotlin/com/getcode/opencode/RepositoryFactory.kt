package com.getcode.opencode

import android.content.Context
import com.getcode.opencode.inject.OpenCodeModule
import com.getcode.opencode.internal.domain.mapping.MessageMapper
import com.getcode.opencode.internal.domain.mapping.TransactionMetadataMapper
import com.getcode.opencode.internal.network.api.MessagingApi
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.opencode.repositories.TransactionRepository
import dagger.hilt.android.EntryPointAccessors

object RepositoryFactory {
    fun createMessagingRepository(context: Context, config: ProtocolConfig): MessagingRepository {
        val appContext = context.applicationContext ?: throw IllegalStateException(
            "applicationContext was not provided",
        )

        val module =  EntryPointAccessors.fromApplication(
            appContext,
            OpenCodeModule::class.java,
        )

        val api = MessagingApi(module.provideManagedChannel(context, config))
        val mapper = MessageMapper()
        val service = MessagingService(api, module.provideNetworkOracle(), mapper)
        return module.providesMessagingRepository(service)
    }

    fun createTransactionRepository(context: Context, config: ProtocolConfig): TransactionRepository {
        val appContext = context.applicationContext ?: throw IllegalStateException(
            "applicationContext was not provided",
        )

        val module =  EntryPointAccessors.fromApplication(
            appContext,
            OpenCodeModule::class.java,
        )

        val api = TransactionApi(module.provideManagedChannel(context, config))
        val mapper = TransactionMetadataMapper()
        val service = TransactionService(api, module.provideNetworkOracle(), mapper)
        return module.providesTransactionRepository(service)
    }
}