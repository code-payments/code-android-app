package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.activity.v1.Model
import com.getcode.opencode.model.core.ID

internal fun Model.NotificationId.toId(): ID = value.toByteArray().toList()