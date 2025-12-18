import SwiftUI
import SampleKit

// MARK: - Demo Types

enum Demo: String, CaseIterable, Identifiable {
    case user = "User Repository"
    case products = "E-commerce"
    case chat = "Chat/Messaging"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .user: return "person.circle"
        case .products: return "cart.circle"
        case .chat: return "message.circle"
        }
    }

    var description: String {
        switch self {
        case .user: return "Basic async/await demo"
        case .products: return "Cart, checkout, price watching"
        case .chat: return "Real-time messaging"
        }
    }
}

// MARK: - Main Content View

struct ContentView: View {
    @State private var selectedDemo: Demo? = .user

    var body: some View {
        NavigationSplitView {
            // Sidebar
            List(Demo.allCases, selection: $selectedDemo) { demo in
                NavigationLink(value: demo) {
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(demo.rawValue)
                                .font(.headline)
                            Text(demo.description)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    } icon: {
                        Image(systemName: demo.icon)
                            .font(.title2)
                            .foregroundColor(.accentColor)
                    }
                    .padding(.vertical, 4)
                }
            }
            .listStyle(.sidebar)
            .navigationSplitViewColumnWidth(min: 200, ideal: 250)
        } detail: {
            // Detail View
            if let demo = selectedDemo {
                switch demo {
                case .user:
                    UserDemoView()
                case .products:
                    ProductDemoView()
                case .chat:
                    ChatDemoView()
                }
            } else {
                Text("Select a demo from the sidebar")
                    .foregroundColor(.secondary)
            }
        }
        .frame(minWidth: 900, minHeight: 600)
    }
}

// MARK: - User Demo

struct UserDemoView: View {
    @State private var isLoading = false
    @State private var logs: [LogEntry] = []
    private let repository = UserRepository()

    var body: some View {
        VStack(spacing: 0) {
            // Header
            DemoHeader(
                title: "User Repository Demo",
                subtitle: "Demonstrates suspend → async/await bridging"
            )

            Divider()

            HStack(spacing: 20) {
                // Controls
                VStack(alignment: .leading, spacing: 16) {
                    Text("Actions").font(.headline)

                    ActionButton(title: "Fetch User", icon: "person.fill", color: .blue) {
                        await testFetchUser()
                    }

                    ActionButton(title: "Login", icon: "arrow.right.circle.fill", color: .green) {
                        await testLogin()
                    }

                    ActionButton(title: "Logout", icon: "arrow.left.circle.fill", color: .orange) {
                        await testLogout()
                    }

                    ActionButton(title: "Subscribe Updates", icon: "bell.fill", color: .purple) {
                        await subscribeToUpdates()
                    }

                    Spacer()
                }
                .frame(width: 200)
                .padding()

                Divider()

                // Log view
                LogView(logs: logs, isLoading: isLoading)
            }
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
            log("Login result: \(result)", type: .success)
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
            log("Logged out successfully", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func subscribeToUpdates() async {
        log("Subscribing to user updates (Flow → AsyncStream)...")
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
        VStack(spacing: 0) {
            DemoHeader(
                title: "E-commerce Demo",
                subtitle: "Products, cart, checkout with real-time price updates"
            )

            Divider()

            HStack(spacing: 0) {
                // Controls
                VStack(alignment: .leading, spacing: 12) {
                    Text("Products").font(.headline)

                    ActionButton(title: "Load Products", icon: "square.grid.2x2", color: .blue) {
                        await loadProducts()
                    }

                    ActionButton(title: "Search", icon: "magnifyingglass", color: .blue) {
                        await searchProducts()
                    }

                    Divider().padding(.vertical, 8)

                    Text("Cart").font(.headline)

                    HStack {
                        Image(systemName: "cart")
                        Text("\(cartItems) items")
                        Spacer()
                        Text("$\(String(format: "%.2f", cartTotal))")
                            .fontWeight(.bold)
                    }
                    .padding(8)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(8)

                    ActionButton(title: "Add to Cart", icon: "plus.circle", color: .green) {
                        await addToCart()
                    }

                    ActionButton(title: "Checkout", icon: "creditcard", color: .orange) {
                        await checkout()
                    }

                    Divider().padding(.vertical, 8)

                    Text("Real-time").font(.headline)

                    ActionButton(title: "Watch Prices", icon: "chart.line.uptrend.xyaxis", color: .purple) {
                        await watchPrices()
                    }

                    Spacer()
                }
                .frame(width: 200)
                .padding()

                Divider()

                // Products list
                VStack(alignment: .leading, spacing: 8) {
                    Text("Products").font(.headline).padding(.horizontal)

                    if products.isEmpty {
                        Spacer()
                        Text("Click 'Load Products' to fetch data")
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                        Spacer()
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
                                    .frame(width: 10, height: 10)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                .frame(minWidth: 250)

                Divider()

                // Log view
                LogView(logs: logs, isLoading: isLoading)
            }
        }
    }

    func loadProducts() async {
        log("Loading products...")
        isLoading = true
        do {
            let page = try await repository.getProducts(page: 1, pageSize: 5, category: nil)
            products = page.products
            log("Loaded \(page.products.count) products (page \(page.currentPage)/\(page.totalPages))", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func searchProducts() async {
        log("Searching for 'iPhone'...")
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
            log("Cart updated: \(cartItems) items, $\(String(format: "%.2f", cartTotal))", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func checkout() async {
        log("Processing checkout...")
        isLoading = true
        do {
            let result = try await repository.checkout(paymentMethod: "credit_card")
            log("Order result: \(result)", type: .success)
            cartItems = 0
            cartTotal = 0
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func watchPrices() async {
        log("Watching price changes (Flow → AsyncStream)...")
        isLoading = true
        var count = 0
        for await update in repository.watchPriceChanges(productId: "prod_1") {
            count += 1
            log("Price: $\(String(format: "%.2f", update.oldPrice)) → $\(String(format: "%.2f", update.newPrice))", type: .info)
            if count >= 3 { break }
        }
        log("Received \(count) price updates", type: .success)
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
        VStack(spacing: 0) {
            DemoHeader(
                title: "Chat/Messaging Demo",
                subtitle: "Real-time messaging with connection state"
            )

            Divider()

            HStack(spacing: 0) {
                // Controls
                VStack(alignment: .leading, spacing: 12) {
                    Text("Connection").font(.headline)

                    HStack {
                        Circle()
                            .fill(isConnected ? Color.green : Color.red)
                            .frame(width: 10, height: 10)
                        Text(isConnected ? "Connected" : "Disconnected")
                    }
                    .padding(8)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)

                    HStack(spacing: 8) {
                        ActionButton(title: "Connect", icon: "wifi", color: .green, compact: true) {
                            await connect()
                        }
                        ActionButton(title: "Disconnect", icon: "wifi.slash", color: .red, compact: true) {
                            await disconnect()
                        }
                    }

                    Divider().padding(.vertical, 8)

                    Text("Messages").font(.headline)

                    ActionButton(title: "Load Conversations", icon: "bubble.left.and.bubble.right", color: .blue) {
                        await loadConversations()
                    }

                    ActionButton(title: "Load Messages", icon: "text.bubble", color: .blue) {
                        await loadMessages()
                    }

                    ActionButton(title: "Send Message", icon: "paperplane.fill", color: .green) {
                        await sendMessage()
                    }

                    Divider().padding(.vertical, 8)

                    Text("Real-time").font(.headline)

                    ActionButton(title: "Watch Messages", icon: "bell.badge", color: .purple) {
                        await watchMessages()
                    }

                    ActionButton(title: "Watch Typing", icon: "ellipsis.bubble", color: .purple) {
                        await watchTyping()
                    }

                    Spacer()
                }
                .frame(width: 200)
                .padding()

                Divider()

                // Conversations & Messages
                VStack(spacing: 0) {
                    // Conversations
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Conversations").font(.headline).padding(.horizontal)
                        if conversations.isEmpty {
                            Text("No conversations loaded")
                                .foregroundColor(.secondary)
                                .padding()
                        } else {
                            List(conversations, id: \.id) { conv in
                                HStack {
                                    Circle()
                                        .fill(conv.isOnline ? Color.green : Color.gray)
                                        .frame(width: 8, height: 8)
                                    VStack(alignment: .leading) {
                                        Text(conv.participantName).font(.headline)
                                        Text(conv.lastMessage).font(.caption).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    if conv.unreadCount > 0 {
                                        Text("\(conv.unreadCount)")
                                            .font(.caption)
                                            .padding(4)
                                            .background(Color.blue)
                                            .foregroundColor(.white)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                    }
                    .frame(height: 180)

                    Divider()

                    // Messages
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Messages").font(.headline).padding(.horizontal)
                        if messages.isEmpty {
                            Text("No messages loaded")
                                .foregroundColor(.secondary)
                                .padding()
                            Spacer()
                        } else {
                            ScrollView {
                                VStack(spacing: 8) {
                                    ForEach(messages.prefix(10), id: \.id) { msg in
                                        HStack {
                                            if msg.senderId == "me" { Spacer() }
                                            Text(msg.content)
                                                .padding(8)
                                                .background(msg.senderId == "me" ? Color.blue : Color.gray.opacity(0.3))
                                                .foregroundColor(msg.senderId == "me" ? .white : .primary)
                                                .cornerRadius(12)
                                            if msg.senderId != "me" { Spacer() }
                                        }
                                    }
                                }
                                .padding()
                            }
                        }
                    }
                }
                .frame(minWidth: 280)

                Divider()

                // Log view
                LogView(logs: logs, isLoading: isLoading)
            }
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
        log("Sending message...")
        isLoading = true
        do {
            let msg = try await repository.sendMessage(conversationId: "conv_1", content: "Hello from Swift!")
            messages.insert(msg, at: 0)
            log("Message sent: \(msg.id)", type: .success)
        } catch {
            log("Error: \(error.localizedDescription)", type: .error)
        }
        isLoading = false
    }

    func watchMessages() async {
        log("Watching for new messages (Flow → AsyncStream)...")
        isLoading = true
        var count = 0
        for await msg in repository.watchMessages(conversationId: "conv_1") {
            count += 1
            messages.insert(msg, at: 0)
            log("New message: \(msg.content)", type: .info)
            if count >= 3 { break }
        }
        log("Received \(count) messages", type: .success)
        isLoading = false
    }

    func watchTyping() async {
        log("Watching typing status...")
        isLoading = true
        var count = 0
        for await typing in repository.watchTypingStatus(conversationId: "conv_1") {
            count += 1
            let status = typing.isTyping ? "\(typing.userName) is typing..." : "\(typing.userName) stopped typing"
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

struct DemoHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
            Text(subtitle)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

struct ActionButton: View {
    let title: String
    let icon: String
    let color: Color
    var compact: Bool = false
    let action: () async -> Void

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            if compact {
                Image(systemName: icon)
                    .foregroundColor(color)
            } else {
                HStack {
                    Image(systemName: icon)
                        .foregroundColor(color)
                    Text(title)
                        .foregroundColor(.primary)
                    Spacer()
                }
            }
        }
        .buttonStyle(.plain)
        .padding(compact ? 8 : 10)
        .background(color.opacity(0.1))
        .cornerRadius(8)
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

struct LogView: View {
    let logs: [LogEntry]
    let isLoading: Bool

    private let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Activity Log")
                    .font(.headline)
                Spacer()
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                }
            }
            .padding(.horizontal)

            if logs.isEmpty {
                Spacer()
                Text("No activity yet")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                Spacer()
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
        .frame(minWidth: 280)
        .padding(.top)
    }
}

#Preview {
    ContentView()
}
