package com.flipcash.app.payments.inject

import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.internal.InternalPaymentController
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentsModule {
    @Provides
    @Singleton
    internal fun providePaymentController(
        resources: ResourceHelper,
        userManager: UserManager,
    ): PaymentController {
        return InternalPaymentController(
            resources = resources,
            userManager = userManager,
        )
    }
}