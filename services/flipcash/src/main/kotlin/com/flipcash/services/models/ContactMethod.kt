package com.flipcash.services.models

sealed interface ContactMethod {
    data class Phone(val phoneNumber: String) : ContactMethod
    data class Email(val emailAddress: String, val clientData: String = "") : ContactMethod
}
