package com.getcode.oct24.network.controllers

import xyz.flipchat.services.internal.network.repository.push.PushRepository
import javax.inject.Inject

class PushController @Inject constructor(
    private val repository: PushRepository
) {

}