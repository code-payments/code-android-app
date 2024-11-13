package com.getcode.network

import com.getcode.model.Kin
import com.getcode.model.intents.IntentType
import com.getcode.network.repository.TransactionRepository
import com.getcode.services.analytics.AnalyticsService
import com.getcode.solana.organizer.Organizer
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyMigration @Inject constructor(
    internal val transactionRepository: TransactionRepository,
    private val analyticsManager: AnalyticsService,
) {

    fun migrateToPrivacy(
        amountToMigrate: Kin,
        organizer: Organizer
    ): Single<IntentType> {
        Timber.i("Start MigrateToPrivacy")
        return transactionRepository.createAccounts(organizer = organizer)
            .doOnSuccess { }
            .flatMap {
                transactionRepository.migrateToPrivacy(
                    amount = amountToMigrate,
                    organizer = organizer,
                )
            }
            .flatMap {
                // There's nothing to receive if we're
                // migrating an empty account
                if (amountToMigrate > 0) {
                    transactionRepository.receiveFromPrimary(
                        amount = amountToMigrate,
                        organizer = organizer
                    )
                } else {
                    Single.just(it)
                }
            }
    }

}