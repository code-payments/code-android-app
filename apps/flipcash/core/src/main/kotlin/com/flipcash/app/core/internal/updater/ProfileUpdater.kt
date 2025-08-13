package com.flipcash.app.core.internal.updater

import com.flipcash.app.core.updater.NetworkUpdater
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class ProfileUpdater @Inject constructor(
    private val profileController: ProfileController,
    private val contactVerificationController: ContactVerificationController,
) : NetworkUpdater() {

    private var clearedInitialLinks: Boolean = false

    override fun poll(
        key: Any?,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration
    ) {
        stop()
        updater = fixedRateTimer(
            name = "update profile",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                coroutineScope {
                    profileController.updateUserProfile()
//                        .onSuccess { profile ->
//                            if (!clearedInitialLinks) {
//                                clearedInitialLinks = true
//                                launch(Dispatchers.IO) {
//                                    if (profile.verifiedPhoneNumber != null) {
//                                        contactVerificationController.unlink(
//                                            ContactMethod.Phone(
//                                                profile.verifiedPhoneNumber!!
//                                            )
//                                        )
//                                            .onSuccess {
//                                                println("unlinked phone")
//                                                profileController.updateUserProfile()
//                                            }
//                                    }
//                                }
//
//                                launch(Dispatchers.IO) {
//                                    if (profile.verifiedEmailAddress != null) {
//                                        contactVerificationController.unlink(
//                                            ContactMethod.Email(
//                                                profile.verifiedEmailAddress!!
//                                            )
//                                        )
//                                            .onSuccess {
//                                                println("unlinked email")
//                                                profileController.updateUserProfile()
//                                            }
//                                    }
//                                }
//
//                            }
//                        }
                }
            }
        }
    }
}