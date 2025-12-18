package com.example

import io.swiftify.annotations.SwiftAsync
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Sample repository with suspend functions and Flow.
 */
class UserRepository {

    private val _currentUser = MutableStateFlow<User?>(null)

    /**
     * Current user as a StateFlow.
     */
    @SwiftFlow
    val currentUser: StateFlow<User?> = _currentUser

    /**
     * Fetch user by ID - transforms to Swift async function.
     */
    @SwiftAsync
    suspend fun fetchUser(id: String): User {
        return User(id = id, name = "John Doe", email = "john@example.com")
    }

    /**
     * Fetch user with optional parameters.
     */
    @SwiftAsync(throwing = true)
    suspend fun fetchUserWithOptions(
        id: String,
        includeProfile: Boolean = true,
        limit: Int = 10
    ): User {
        return User(id = id, name = "John", email = "john@example.com")
    }

    /**
     * Get user updates as a Flow - transforms to AsyncSequence.
     */
    @SwiftFlow
    fun getUserUpdates(userId: String): Flow<User> {
        throw NotImplementedError("Stub")
    }

    /**
     * Login user.
     */
    @SwiftAsync
    suspend fun login(username: String, password: String): NetworkResult<User> {
        return NetworkResult.Success(User("1", username, "$username@example.com"))
    }

    /**
     * Logout user.
     */
    @SwiftAsync
    suspend fun logout() {
        _currentUser.value = null
    }
}

/**
 * Simple User data class.
 */
data class User(
    val id: String,
    val name: String,
    val email: String
)
