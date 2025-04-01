package com.getcode.util.permissions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(impl: AndroidPermissions): PermissionChecker
}