package com.flipcash.app.core.android

import javax.inject.Inject

class VersionInfo @Inject constructor(
    val versionName: String = "",
    val versionCode: Int = 0,
)