package com.flipcash.app.messenger.internal.payment

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class StartChattingModule {
    @Binds
    abstract fun bindStartChattingPayer(impl: DefaultStartChattingPayer): StartChattingPayer
}
