import SwiftUI
import SampleKit

// MARK: - Demo Types

enum Demo: String, CaseIterable, Identifiable {
    case user = "User"
    case products = "Products"
    case chat = "Chat"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .user: return "person.circle.fill"
        case .products: return "cart.circle.fill"
        case .chat: return "message.circle.fill"
        }
    }

    var description: String {
        switch self {
        case .user: return "Async/Await"
        case .products: return "E-commerce"
        case .chat: return "Real-time"
        }
    }
}

// MARK: - Main Content View

struct ContentView: View {
    @State private var selectedTab: Demo = .user

    var body: some View {
        TabView(selection: $selectedTab) {
            UserDemoView()
                .tabItem {
                    Label(Demo.user.rawValue, systemImage: Demo.user.icon)
                }
                .tag(Demo.user)

            ProductDemoView()
                .tabItem {
                    Label(Demo.products.rawValue, systemImage: Demo.products.icon)
                }
                .tag(Demo.products)

            ChatDemoView()
                .tabItem {
                    Label(Demo.chat.rawValue, systemImage: Demo.chat.icon)
                }
                .tag(Demo.chat)
        }
    }
}

// MARK: - User Demo

struct UserDemoView: View {
    @State private var isLoading = false
    @State private var logs: [LogEntry] = []
    private let repository = UserRepository()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Actions Grid
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    ActionCard(title: "Fetch User", icon: "person.fill", color: .blue) {
                        await testFetchUser()
                    }

                    ActionCard(title: "Login", icon: "arrow.right.circle.fill", color: .green) {
                        await testLogin()
                    }

                    ActionCard(title: "Logout", icon: "arrow.left.circle.fill", color: .orange) {
                        await testLogout()
                    }

                    ActionCard(title: "Updates", icon: "bell.fill", color: .purple) {
                        await subscribeToUpdates()
                    }
                }
                .padding()

                Divider()

                // Log view
                LogListView(logs: logs, isLoading: isLoading)
            }
            .navigationTitle("User Repository")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    func testFetchUser() async {
        log("Fetching user...")
        isLoading = true
        do {
            let user = try await repository.fetchUser(id: "user123")
            log("User: \(user.name) (\(user.email))", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func testLogin() async {
        log("Logging in...")
        isLoading = true
        do {
            let result = try await repository.login(username: "john", password: "pass")
            log("Login: \(result)", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func testLogout() async {
        log("Logging out...")
        isLoading = true
        do {
            try await repository.logout()
            log("Logged out", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func subscribeToUpdates() async {
        log("Subscribing to updates...")
        isLoading = true
        var count = 0
        for await user in repository.getUserUpdates(userId: "user123") {
            count += 1
            log("Update #\(count): \(user.name)", type: .info)
            if count >= 3 { break }
        }
        log("Received \(count) updates", type: .success)
        isLoading = false
    }

    func log(_ message: String, type: LogEntry.LogType = .info) {
        logs.insert(LogEntry(message: message, type: type), at: 0)
    }
}

// MARK: - Product Demo

struct ProductDemoView: View {
    @State private var isLoading = false
    @State private var logs: [LogEntry] = []
    @State private var products: [Product] = []
    @State private var cartItems: Int = 0
    @State private var cartTotal: Double = 0
    private let repository = ProductRepository()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Cart summary
                HStack {
                    Image(systemName: "cart.fill")
                        .foregroundColor(.blue)
                    Text("\(cartItems) items")
                    Spacer()
                    Text("$\(String(format: "%.2f", cartTotal))")
                        .fontWeight(.bold)
                }
                .padding()
                .background(Color.blue.opacity(0.1))

                // Actions
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        CompactActionButton(title: "Load", icon: "arrow.down.circle", color: .blue) {
                            await loadProducts()
                        }

                        CompactActionButton(title: "Search", icon: "magnifyingglass", color: .blue) {
                            await searchProducts()
                        }

                        CompactActionButton(title: "Add", icon: "plus.circle", color: .green) {
                            await addToCart()
                        }

                        CompactActionButton(title: "Checkout", icon: "creditcard", color: .orange) {
                            await checkout()
                        }

                        CompactActionButton(title: "Prices", icon: "chart.line.uptrend.xyaxis", color: .purple) {
                            await watchPrices()
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical, 8)

                Divider()

                // Products list
                if products.isEmpty {
                    VStack {
                        Image(systemName: "bag")
                            .font(.largeTitle)
                            .foregroundColor(.secondary)
                        Text("Tap 'Load' to fetch products")
                            .foregroundColor(.secondary)
                    }
                    .frame(height: 150)
                } else {
                    List(products, id: \.id) { product in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(product.name).font(.headline)
                                Text(product.category).font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Text("$\(String(format: "%.2f", product.price))")
                                .fontWeight(.semibold)
                            Circle()
                                .fill(product.inStock ? Color.green : Color.red)
                                .frame(width: 8, height: 8)
                        }
                    }
                    .listStyle(.plain)
                    .frame(height: 180)
                }

                Divider()

                // Logs
                LogListView(logs: logs, isLoading: isLoading)
            }
            .navigationTitle("E-commerce")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    func loadProducts() async {
        log("Loading products...")
        isLoading = true
        do {
            let page = try await repository.getProducts(page: 1, pageSize: 5, category: nil)
            products = page.products
            log("Loaded \(page.products.count) products", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func searchProducts() async {
        log("Searching...")
        isLoading = true
        do {
            let results = try await repository.searchProducts(query: "iPhone", minPrice: 0, maxPrice: 100, inStockOnly: false)
            products = results
            log("Found \(results.count) products", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func addToCart() async {
        log("Adding to cart...")
        isLoading = true
        do {
            let cart = try await repository.addToCart(productId: "prod_1", quantity: 1)
            cartItems = Int(cart.items.count)
            cartTotal = cart.total
            log("Cart: \(cartItems) items, $\(String(format: "%.2f", cartTotal))", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func checkout() async {
        log("Checking out...")
        isLoading = true
        do {
            let result = try await repository.checkout(paymentMethod: "credit_card")
            log("Order: \(result)", type: .success)
            cartItems = 0
            cartTotal = 0
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func watchPrices() async {
        log("Watching prices...")
        isLoading = true
        var count = 0
        for await update in repository.watchPriceChanges(productId: "prod_1") {
            count += 1
            log("$\(String(format: "%.2f", update.oldPrice)) → $\(String(format: "%.2f", update.newPrice))", type: .info)
            if count >= 3 { break }
        }
        log("Received \(count) updates", type: .success)
        isLoading = false
    }

    func log(_ message: String, type: LogEntry.LogType = .info) {
        logs.insert(LogEntry(message: message, type: type), at: 0)
    }
}

// MARK: - Chat Demo

struct ChatDemoView: View {
    @State private var isLoading = false
    @State private var logs: [LogEntry] = []
    @State private var isConnected = false
    @State private var conversations: [Conversation] = []
    @State private var messages: [Message] = []
    private let repository = ChatRepository()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Connection status
                HStack {
                    Circle()
                        .fill(isConnected ? Color.green : Color.red)
                        .frame(width: 10, height: 10)
                    Text(isConnected ? "Connected" : "Disconnected")
                        .font(.subheadline)
                    Spacer()

                    Button(action: { Task { await connect() } }) {
                        Image(systemName: "wifi")
                            .foregroundColor(.green)
                    }
                    .padding(.horizontal, 8)

                    Button(action: { Task { await disconnect() } }) {
                        Image(systemName: "wifi.slash")
                            .foregroundColor(.red)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))

                // Actions
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        CompactActionButton(title: "Convos", icon: "bubble.left.and.bubble.right", color: .blue) {
                            await loadConversations()
                        }

                        CompactActionButton(title: "Messages", icon: "text.bubble", color: .blue) {
                            await loadMessages()
                        }

                        CompactActionButton(title: "Send", icon: "paperplane.fill", color: .green) {
                            await sendMessage()
                        }

                        CompactActionButton(title: "Watch", icon: "bell.badge", color: .purple) {
                            await watchMessages()
                        }

                        CompactActionButton(title: "Typing", icon: "ellipsis.bubble", color: .purple) {
                            await watchTyping()
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical, 8)

                Divider()

                // Messages
                if messages.isEmpty {
                    VStack {
                        Image(systemName: "message")
                            .font(.largeTitle)
                            .foregroundColor(.secondary)
                        Text("Tap 'Messages' to load")
                            .foregroundColor(.secondary)
                    }
                    .frame(height: 120)
                } else {
                    ScrollView {
                        VStack(spacing: 8) {
                            ForEach(messages.prefix(8), id: \.id) { msg in
                                HStack {
                                    if msg.senderId == "me" { Spacer() }
                                    Text(msg.content)
                                        .padding(10)
                                        .background(msg.senderId == "me" ? Color.blue : Color.gray.opacity(0.3))
                                        .foregroundColor(msg.senderId == "me" ? .white : .primary)
                                        .cornerRadius(16)
                                    if msg.senderId != "me" { Spacer() }
                                }
                            }
                        }
                        .padding()
                    }
                    .frame(height: 140)
                }

                Divider()

                // Logs
                LogListView(logs: logs, isLoading: isLoading)
            }
            .navigationTitle("Chat")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    func connect() async {
        log("Connecting...")
        isLoading = true
        do {
            let state = try await repository.connect()
            isConnected = true
            log("Connected: \(state)", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func disconnect() async {
        log("Disconnecting...")
        isLoading = true
        do {
            try await repository.disconnect()
            isConnected = false
            log("Disconnected", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func loadConversations() async {
        log("Loading conversations...")
        isLoading = true
        do {
            conversations = try await repository.getConversations(page: 1, includeArchived: false)
            log("Loaded \(conversations.count) conversations", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func loadMessages() async {
        log("Loading messages...")
        isLoading = true
        do {
            let page = try await repository.getMessages(conversationId: "conv_1", beforeMessageId: nil, limit: 10)
            messages = page.messages
            log("Loaded \(page.messages.count) messages", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func sendMessage() async {
        log("Sending...")
        isLoading = true
        do {
            let msg = try await repository.sendMessage(conversationId: "conv_1", content: "Hello from iOS!")
            messages.insert(msg, at: 0)
            log("Sent: \(msg.id)", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func watchMessages() async {
        log("Watching messages...")
        isLoading = true
        var count = 0
        for await msg in repository.watchMessages(conversationId: "conv_1") {
            count += 1
            messages.insert(msg, at: 0)
            log("New: \(msg.content)", type: .info)
            if count >= 3 { break }
        }
        log("Received \(count) messages", type: .success)
        isLoading = false
    }

    func watchTyping() async {
        log("Watching typing...")
        isLoading = true
        var count = 0
        for await typing in repository.watchTypingStatus(conversationId: "conv_1") {
            count += 1
            let status = typing.isTyping ? "\(typing.userName) typing..." : "\(typing.userName) stopped"
            log(status, type: .info)
            if count >= 2 { break }
        }
        isLoading = false
    }

    func log(_ message: String, type: LogEntry.LogType = .info) {
        logs.insert(LogEntry(message: message, type: type), at: 0)
    }
}

// MARK: - Shared Components

struct ActionCard: View {
    let title: String
    let icon: String
    let color: Color
    let action: () async -> Void

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(color)
                Text(title)
                    .font(.caption)
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(color.opacity(0.1))
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}

struct CompactActionButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () async -> Void

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundColor(color)
                Text(title)
                    .font(.caption2)
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(color.opacity(0.1))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct LogEntry: Identifiable {
    let id = UUID()
    let timestamp = Date()
    let message: String
    let type: LogType

    enum LogType {
        case info, success, error

        var color: Color {
            switch self {
            case .info: return .primary
            case .success: return .green
            case .error: return .red
            }
        }

        var icon: String {
            switch self {
            case .info: return "info.circle"
            case .success: return "checkmark.circle"
            case .error: return "xmark.circle"
            }
        }
    }
}

struct LogListView: View {
    let logs: [LogEntry]
    let isLoading: Bool

    private let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Activity")
                    .font(.headline)
                Spacer()
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)

            if logs.isEmpty {
                Text("No activity yet")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(logs) { entry in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: entry.type.icon)
                            .foregroundColor(entry.type.color)
                            .font(.caption)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.message)
                                .font(.caption)
                                .foregroundColor(entry.type.color)
                            Text(timeFormatter.string(from: entry.timestamp))
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
    }
}

#Preview {
    ContentView()
}
