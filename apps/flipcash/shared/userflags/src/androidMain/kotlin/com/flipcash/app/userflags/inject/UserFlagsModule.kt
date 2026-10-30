package com.flipcash.app.userflags.inject

import com.flipcash.app.userflags.AndroidUserFlagsFiles
import com.flipcash.app.userflags.UserFlagsFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UserFlagsModule {
    @Binds
    abstract fun bindUserFlagsFiles(impl: AndroidUserFlagsFiles): UserFlagsFiles
}
