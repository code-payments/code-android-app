package com.getcode.network.jwt

typealias Jwt = String

interface JwtProvider {
    /**
     * Provider for a short lived token for accessing specific endpoints.
     *
     * @param apiKey the api key for the endpoint.
     * @param endpoint the endpoint to access.
     *
     * @return a short lived token for access to the endpoint.
     */
    suspend fun provideJwtForEndpoint(
        apiKey: String,
        endpoint: JwtSecuredEndpoint,
    ): Result<Jwt>
}