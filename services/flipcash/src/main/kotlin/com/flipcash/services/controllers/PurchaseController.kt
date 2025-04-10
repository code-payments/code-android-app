package com.flipcash.services.controllers

import com.flipcash.services.billing.BillingClient
import javax.inject.Inject

class PurchaseController @Inject constructor(
    private val billingClient: BillingClient,
) {
}