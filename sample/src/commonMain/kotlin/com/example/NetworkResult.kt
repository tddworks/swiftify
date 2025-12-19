package com.example

import io.swiftify.annotations.SwiftEnum

/**
 * Sample sealed class that should transform to Swift enum.
 */
@SwiftEnum(name = "NetworkResult", exhaustive = true)
sealed class NetworkResult<out T> {
    data class Success<T>(
        val data: T,
    ) : NetworkResult<T>()

    data class Error(
        val message: String,
        val code: Int,
    ) : NetworkResult<Nothing>()

    data object Loading : NetworkResult<Nothing>()
}

/**
 * Another sealed class for testing.
 */
sealed class AuthState {
    data object LoggedOut : AuthState()

    data class LoggedIn(
        val userId: String,
        val token: String,
    ) : AuthState()

    data class Error(
        val reason: String,
    ) : AuthState()
}
