import SwiftUI
import SampleKit

struct ContentView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            UserDemoView()
                .tabItem { Label("User", systemImage: "person") }
                .tag(0)

            ProductDemoView()
                .tabItem { Label("Products", systemImage: "cart") }
                .tag(1)

            ChatDemoView()
                .tabItem { Label("Chat", systemImage: "message") }
                .tag(2)
        }
        .frame(minWidth: 700, minHeight: 500)
    }
}

// MARK: - User Demo (Original)

struct UserDemoView: View {
    @State private var statusText = "Ready to test"
    @State private var isLoading = false
    private let repository = UserRepository()

    var body: some View {
        VStack(spacing: 16) {
            Text("User Repository Demo")
                .font(.title2).bold()

            Text(statusText)
                .foregroundColor(.secondary)
                .frame(height: 40)

            if isLoading { ProgressView() }

            HStack(spacing: 12) {
                Button("Fetch User") { Task { await testFetchUser() } }
                Button("Login") { Task { await testLogin() } }
                Button("Logout") { Task { await testLogout() } }
            }
        }
        .padding()
    }

    func testFetchUser() async {
        isLoading = true
        statusText = "Fetching..."
        do {
            let user = try await repository.fetchUser(id: "user123")
            statusText = "User: \(user.name) (\(user.email))"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func testLogin() async {
        isLoading = true
        statusText = "Logging in..."
        do {
            let result = try await repository.login(username: "john", password: "pass")
            statusText = "Login result: \(result)"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func testLogout() async {
        isLoading = true
        do {
            try await repository.logout()
            statusText = "Logged out"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }
}

// MARK: - Product Demo (E-commerce)

struct ProductDemoView: View {
    @State private var statusText = "Ready"
    @State private var isLoading = false
    @State private var products: [SampleKitProduct] = []
    @State private var cartItems: Int = 0
    @State private var cartTotal: Double = 0
    private let repository = ProductRepository()

    var body: some View {
        VStack(spacing: 16) {
            Text("E-commerce Demo")
                .font(.title2).bold()

            HStack {
                Text(statusText)
                    .foregroundColor(.secondary)
                Spacer()
                Text("Cart: \(cartItems) items ($\(String(format: "%.2f", cartTotal)))")
                    .foregroundColor(.blue)
            }
            .frame(height: 30)

            if isLoading { ProgressView() }

            HStack(spacing: 12) {
                Button("Load Products") { Task { await loadProducts() } }
                Button("Search") { Task { await searchProducts() } }
                Button("Product Details") { Task { await getProductDetails() } }
            }

            HStack(spacing: 12) {
                Button("Add to Cart") { Task { await addToCart() } }
                Button("Remove from Cart") { Task { await removeFromCart() } }
                Button("Checkout") { Task { await checkout() } }
            }

            HStack(spacing: 12) {
                Button("Watch Prices") { Task { await watchPrices() } }
                Button("Watch Cart") { Task { await watchCart() } }
            }

            // Product list
            if !products.isEmpty {
                List(products, id: \.id) { product in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(product.name).font(.headline)
                            Text(product.category).font(.caption).foregroundColor(.secondary)
                        }
                        Spacer()
                        Text("$\(String(format: "%.2f", product.price))")
                        Circle()
                            .fill(product.inStock ? Color.green : Color.red)
                            .frame(width: 10, height: 10)
                    }
                }
                .frame(height: 200)
            }
        }
        .padding()
    }

    func loadProducts() async {
        isLoading = true
        statusText = "Loading products..."
        do {
            // Using default parameters - page: 1, pageSize: 20
            let page = try await repository.getProducts(page: 1, pageSize: 5, category: nil)
            products = page.products
            statusText = "Loaded \(page.products.count) products (page \(page.currentPage)/\(page.totalPages))"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func searchProducts() async {
        isLoading = true
        statusText = "Searching..."
        do {
            // Using default parameters for minPrice, maxPrice, inStockOnly
            let results = try await repository.searchProducts(query: "iPhone", minPrice: 0, maxPrice: 100, inStockOnly: false)
            products = results
            statusText = "Found \(results.count) products"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func getProductDetails() async {
        isLoading = true
        statusText = "Loading details..."
        do {
            let details = try await repository.getProductDetails(productId: "prod_1")
            statusText = "\(details.product.name): \(details.description_) - \(details.reviews.count) reviews"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func addToCart() async {
        isLoading = true
        do {
            // Using default quantity = 1
            let cart = try await repository.addToCart(productId: "prod_1", quantity: 1)
            cartItems = Int(cart.items.count)
            cartTotal = cart.total
            statusText = "Added to cart"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func removeFromCart() async {
        isLoading = true
        do {
            let cart = try await repository.removeFromCart(productId: "prod_1")
            cartItems = Int(cart.items.count)
            cartTotal = cart.total
            statusText = "Removed from cart"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func checkout() async {
        isLoading = true
        statusText = "Processing payment..."
        do {
            let result = try await repository.checkout(paymentMethod: "credit_card")
            statusText = "Order result: \(result)"
            cartItems = 0
            cartTotal = 0
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func watchPrices() async {
        isLoading = true
        statusText = "Watching price changes..."

        var count = 0
        for await update in repository.watchPriceChanges(productId: "prod_1") {
            count += 1
            statusText = "Price: $\(String(format: "%.2f", update.oldPrice)) → $\(String(format: "%.2f", update.newPrice))"
            if count >= 3 { break }
        }

        statusText = "Received \(count) price updates"
        isLoading = false
    }

    func watchCart() async {
        statusText = "Watching cart..."
        // StateFlow - get current state immediately
        for await cart in repository.cartStream {
            cartItems = Int(cart.items.count)
            cartTotal = cart.total
            statusText = "Cart updated: \(cart.items.count) items"
            break
        }
    }
}

// MARK: - Chat Demo (Messaging)

struct ChatDemoView: View {
    @State private var statusText = "Disconnected"
    @State private var isLoading = false
    @State private var conversations: [SampleKitConversation] = []
    @State private var messages: [SampleKitMessage] = []
    @State private var unreadCount: Int = 0
    private let repository = ChatRepository()

    var body: some View {
        VStack(spacing: 16) {
            Text("Chat/Messaging Demo")
                .font(.title2).bold()

            HStack {
                Circle()
                    .fill(statusText == "Connected" ? Color.green : Color.red)
                    .frame(width: 10, height: 10)
                Text(statusText)
                    .foregroundColor(.secondary)
                Spacer()
                if unreadCount > 0 {
                    Text("\(unreadCount) unread")
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(Color.red)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
            }

            if isLoading { ProgressView() }

            HStack(spacing: 12) {
                Button("Connect") { Task { await connect() } }
                Button("Disconnect") { Task { await disconnect() } }
                Button("Load Conversations") { Task { await loadConversations() } }
            }

            HStack(spacing: 12) {
                Button("Load Messages") { Task { await loadMessages() } }
                Button("Send Message") { Task { await sendMessage() } }
                Button("Mark Read") { Task { await markAsRead() } }
            }

            HStack(spacing: 12) {
                Button("Watch Messages") { Task { await watchMessages() } }
                Button("Watch Typing") { Task { await watchTyping() } }
            }

            // Conversations list
            if !conversations.isEmpty {
                VStack(alignment: .leading) {
                    Text("Conversations").font(.headline)
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
                    .frame(height: 150)
                }
            }

            // Messages list
            if !messages.isEmpty {
                VStack(alignment: .leading) {
                    Text("Messages").font(.headline)
                    List(messages.prefix(5), id: \.id) { msg in
                        HStack {
                            if msg.senderId == "me" { Spacer() }
                            Text(msg.content)
                                .padding(8)
                                .background(msg.senderId == "me" ? Color.blue : Color.gray.opacity(0.3))
                                .foregroundColor(msg.senderId == "me" ? .white : .primary)
                                .cornerRadius(8)
                            if msg.senderId != "me" { Spacer() }
                        }
                    }
                    .frame(height: 120)
                }
            }
        }
        .padding()
    }

    func connect() async {
        isLoading = true
        statusText = "Connecting..."
        do {
            let state = try await repository.connect()
            statusText = "\(state)"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func disconnect() async {
        isLoading = true
        do {
            try await repository.disconnect()
            statusText = "Disconnected"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func loadConversations() async {
        isLoading = true
        statusText = "Loading..."
        do {
            // Using default parameters
            conversations = try await repository.getConversations(page: 1, includeArchived: false)
            statusText = "Loaded \(conversations.count) conversations"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func loadMessages() async {
        isLoading = true
        statusText = "Loading messages..."
        do {
            // Using default parameters for beforeMessageId and limit
            let page = try await repository.getMessages(conversationId: "conv_1", beforeMessageId: nil, limit: 10)
            messages = page.messages
            statusText = "Loaded \(page.messages.count) messages"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func sendMessage() async {
        isLoading = true
        do {
            let msg = try await repository.sendMessage(conversationId: "conv_1", content: "Hello from Swift!")
            messages.insert(msg, at: 0)
            statusText = "Message sent: \(msg.id)"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func markAsRead() async {
        isLoading = true
        do {
            try await repository.markAsRead(conversationId: "conv_1")
            statusText = "Marked as read"
        } catch {
            statusText = "Error: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func watchMessages() async {
        isLoading = true
        statusText = "Watching for new messages..."

        var count = 0
        for await msg in repository.watchMessages(conversationId: "conv_1") {
            count += 1
            messages.insert(msg, at: 0)
            unreadCount += 1
            statusText = "New message: \(msg.content)"
            if count >= 3 { break }
        }

        statusText = "Received \(count) messages"
        isLoading = false
    }

    func watchTyping() async {
        isLoading = true
        statusText = "Watching typing..."

        var count = 0
        for await typing in repository.watchTypingStatus(conversationId: "conv_1") {
            count += 1
            statusText = typing.isTyping ? "\(typing.userName) is typing..." : ""
            if count >= 2 { break }
        }

        isLoading = false
    }
}

#Preview {
    ContentView()
}
