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

            VStack(spacing: 12) {
                Button("Fetch User") {
                    Task { await testFetchUser() }
                }
                .buttonStyle(.borderedProminent)

                Button("Fetch with Options") {
                    Task { await testFetchWithOptions() }
                }
                .buttonStyle(.borderedProminent)

                Button("Login") {
                    Task { await testLogin() }
                }
                .buttonStyle(.borderedProminent)

                Button("Logout") {
                    Task { await testLogout() }
                }
                .buttonStyle(.bordered)

                Button("Subscribe to Updates") {
                    Task { await testUserUpdates() }
                }
                .buttonStyle(.bordered)
            }
            .disabled(isLoading)

            Spacer()
        }
        .padding()
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
            let user1 = try await repository.fetchUserWithOptions(id: "user123")

            // Or override the defaults
            let user2 = try await repository.fetchUserWithOptions(
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

    /// Test login returning sealed class (mapped to Swift enum)
    func testLogin() async {
        isLoading = true
        statusText = "Logging in..."

        do {
            let result = try await repository.login(
                username: "testuser",
                password: "password123"
            )

            // Handle the NetworkResult enum (generated from Kotlin sealed class)
            switch result {
            case .success(let data):
                statusText = "Login successful! User: \(data)"
            case .error(let message, let code):
                statusText = "Login failed: \(message) (code: \(code))"
            case .loading:
                statusText = "Loading..."
            }
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

    /// Test Flow -> AsyncStream
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
}

#Preview {
    ContentView()
}
