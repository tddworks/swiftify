import SwiftUI
import SampleKit  // Import the Kotlin framework

struct ContentView: View {
    @State private var statusText = "Ready to test"
    @State private var isLoading = false

    // Create an instance of the Kotlin class
    private let repository = UserRepository()

    var body: some View {
        VStack(spacing: 20) {
            Text("Swiftify Demo")
                .font(.largeTitle)
                .fontWeight(.bold)

            Text(statusText)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding()

            if isLoading {
                ProgressView()
            }

            HStack(spacing: 12) {
                Button("Fetch User") {
                    Task { await testFetchUser() }
                }

                Button("Fetch with Options") {
                    Task { await testFetchWithOptions() }
                }

                Button("Login") {
                    Task { await testLogin() }
                }

                Button("Logout") {
                    Task { await testLogout() }
                }
            }
            .disabled(isLoading)

            HStack(spacing: 12) {
                Button("Subscribe to Updates") {
                    Task { await testUserUpdates() }
                }

                Button("Subscribe to Current User") {
                    Task { await testCurrentUser() }
                }
            }
            .disabled(isLoading)

            Spacer()
        }
        .padding()
        .frame(minWidth: 600, minHeight: 400)
    }

    // MARK: - Test Functions using Swiftify-generated extensions

    /// Test basic async function
    func testFetchUser() async {
        isLoading = true
        statusText = "Fetching user..."

        do {
            // Using the Swiftify-generated async extension!
            let user = try await repository.fetchUser(id: "user123")
            statusText = "Fetched user: \(user)"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }

        isLoading = false
    }

    /// Test async function with default parameters
    func testFetchWithOptions() async {
        isLoading = true
        statusText = "Fetching with options..."

        do {
            // Using Swift default parameters!
            // Can call with just id (uses defaults for includeProfile and limit)
            let _ = try await repository.fetchUserWithOptions(id: "user123")

            // Or override the defaults
            let _ = try await repository.fetchUserWithOptions(
                id: "user456",
                includeProfile: false,
                limit: 5
            )

            statusText = "Fetched users with options!"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }

        isLoading = false
    }

    /// Test login returning NetworkResult
    func testLogin() async {
        isLoading = true
        statusText = "Logging in..."

        do {
            let result = try await repository.login(
                username: "testuser",
                password: "password123"
            )

            // NetworkResult is a Kotlin sealed class - check type at runtime
            statusText = "Login result: \(result)"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }

        isLoading = false
    }

    /// Test void async function
    func testLogout() async {
        isLoading = true
        statusText = "Logging out..."

        do {
            try await repository.logout()
            statusText = "Logged out successfully!"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }

        isLoading = false
    }

    /// Test Flow -> AsyncStream (function)
    func testUserUpdates() async {
        isLoading = true
        statusText = "Subscribing to updates..."

        // Using the Swiftify-generated AsyncStream!
        var count = 0
        for await user in repository.getUserUpdates(userId: "user123") {
            count += 1
            statusText = "Update #\(count): \(user)"

            // Stop after 5 updates for demo
            if count >= 5 {
                break
            }
        }

        statusText = "Received \(count) updates"
        isLoading = false
    }

    /// Test StateFlow -> AsyncStream (property)
    func testCurrentUser() async {
        isLoading = true
        statusText = "Subscribing to current user..."

        // Using the Swiftify-generated AsyncStream property!
        // Note: The property is named `currentUserStream` to avoid collision with Kotlin property
        var count = 0
        for await user in repository.currentUserStream {
            count += 1
            statusText = "Current user update #\(count): \(user)"

            // Stop after 3 updates for demo
            if count >= 3 {
                break
            }
        }

        statusText = "Received \(count) current user updates"
        isLoading = false
    }
}

#Preview {
    ContentView()
}
