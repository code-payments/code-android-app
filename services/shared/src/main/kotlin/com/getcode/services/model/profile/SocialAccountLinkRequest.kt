package com.getcode.services.model.profile

typealias LinkingToken = String

sealed interface SocialAccountLinkRequest {
    data class X(val token: LinkingToken): SocialAccountLinkRequest
}

sealed interface SocialAccountUnlinkRequest {
    data class X(val userId: String): SocialAccountUnlinkRequest
}