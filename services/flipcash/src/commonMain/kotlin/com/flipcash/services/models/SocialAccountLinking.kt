package com.flipcash.services.models

typealias LinkingToken = String

sealed interface SocialAccountLinkRequest {
    data class X(val token: LinkingToken): SocialAccountLinkRequest
}

sealed interface SocialAccountUnlinkRequest {
    data class X(val userId: String): SocialAccountUnlinkRequest
}