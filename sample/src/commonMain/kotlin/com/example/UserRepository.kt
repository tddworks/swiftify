package com.example

import io.swiftify.annotations.SwiftDefaults
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Sample repository with suspend functions and Flow.
 */
class UserRepository {

    private val _currentUser = MutableStateFlow(User("current", "Current User", "current@example.com"))

    /**
     * Current user as a StateFlow.
     */
    @SwiftFlow
    val currentUser: StateFlow<User> = _currentUser

    /**
     * Fetch user by ID - transforms to Swift async function.
     */
    @SwiftDefaults
    suspend fun fetchUser(id: String): User {
        delay(500) // Simulate network delay
        return User(id = id, name = "John Doe", email = "john@example.com")
    }

    /**
     * Fetch user with optional parameters.
     */
    @SwiftDefaults
    suspend fun fetchUserWithOptions(
        id: String,
        includeProfile: Boolean = true,
        limit: Int = 10
    ): User {
        delay(500) // Simulate network delay
        return User(id = id, name = "John", email = "john@example.com")
    }

    /**
     * Get user updates as a Flow - transforms to AsyncSequence.
     */
    @SwiftFlow
    fun getUserUpdates(userId: String): Flow<User> = flow {
        var count = 0
        while (true) {
            count++
            emit(User(userId, "User Update #$count", "user$count@example.com"))
            delay(1000) // Emit every second
        }
    }

    /**
     * Login user.
     */
    @SwiftDefaults
    suspend fun login(username: String, password: String): NetworkResult<User> {
        delay(500) // Simulate network delay
        val user = User("1", username, "$username@example.com")
        _currentUser.value = user
        return NetworkResult.Success(user)
    }

    /**
     * Logout user.
     */
    @SwiftDefaults
    suspend fun logout() {
        delay(200) // Simulate network delay
        _currentUser.value = User("guest", "Guest", "guest@example.com")
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
