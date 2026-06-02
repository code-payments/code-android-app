package com.flipcash.app.contacts.inject

import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.device.DeviceContactLookup
import com.flipcash.app.contacts.device.internal.AndroidDeviceContactLookup
import com.getcode.opencode.providers.SessionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactModule {

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        coordinator: ContactCoordinator
    ): SessionListener

    @Binds
    internal abstract fun bindDeviceContactLookup(
        impl: AndroidDeviceContactLookup
    ): DeviceContactLookup
}
