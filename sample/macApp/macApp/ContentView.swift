import SwiftUI
import SampleKit

// MARK: - Design System

struct SwiftifyTheme {
    // Colors
    static let background = Color(hex: "0D0D0D")
    static let surface = Color(hex: "1A1A1A")
    static let surfaceElevated = Color(hex: "252525")
    static let accent = Color(hex: "FF6B35") // Warm orange
    static let accentGlow = Color(hex: "FF6B35").opacity(0.3)
    static let success = Color(hex: "4ADE80")
    static let info = Color(hex: "60A5FA")
    static let textPrimary = Color.white
    static let textSecondary = Color(hex: "A0A0A0")
    static let textMuted = Color(hex: "606060")
    static let border = Color(hex: "333333")
    static let kotlin = Color(hex: "7F52FF") // Kotlin purple
    static let swift = Color(hex: "FF6B35")  // Swift orange

    // Fonts
    static let displayFont = Font.custom("SF Pro Display", size: 48).weight(.bold)
    static let headlineFont = Font.custom("SF Pro Display", size: 24).weight(.semibold)
    static let titleFont = Font.custom("SF Pro Display", size: 18).weight(.medium)
    static let bodyFont = Font.custom("SF Pro Text", size: 14)
    static let codeFont = Font.custom("SF Mono", size: 13)
    static let codeFontSmall = Font.custom("SF Mono", size: 12)
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Main App View

struct ContentView: View {
    @State private var selectedSection: Section = .overview

    enum Section: String, CaseIterable {
        case overview = "Overview"
        case asyncAwait = "Async/Await"
        case defaultParams = "Default Parameters"
        case asyncStream = "AsyncStream"
        case liveDemo = "Live Demo"
    }

    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    SwiftifyTheme.background,
                    Color(hex: "0A0A0A")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Subtle grid pattern
            GeometryReader { geo in
                Path { path in
                    let spacing: CGFloat = 50
                    for i in stride(from: 0, to: geo.size.width, by: spacing) {
                        path.move(to: CGPoint(x: i, y: 0))
                        path.addLine(to: CGPoint(x: i, y: geo.size.height))
                    }
                    for i in stride(from: 0, to: geo.size.height, by: spacing) {
                        path.move(to: CGPoint(x: 0, y: i))
                        path.addLine(to: CGPoint(x: geo.size.width, y: i))
                    }
                }
                .stroke(SwiftifyTheme.border.opacity(0.2), lineWidth: 0.5)
            }
            .ignoresSafeArea()

            HStack(spacing: 0) {
                // Sidebar
                SidebarView(selectedSection: $selectedSection)

                // Divider
                Rectangle()
                    .fill(SwiftifyTheme.border)
                    .frame(width: 1)

                // Main content
                ScrollView {
                    VStack(spacing: 0) {
                        switch selectedSection {
                        case .overview:
                            OverviewSection()
                        case .asyncAwait:
                            AsyncAwaitSection()
                        case .defaultParams:
                            DefaultParamsSection()
                        case .asyncStream:
                            AsyncStreamSection()
                        case .liveDemo:
                            LiveDemoSection()
                        }
                    }
                    .padding(40)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .preferredColorScheme(.dark)
        .frame(minWidth: 1200, minHeight: 800)
    }
}

// MARK: - Sidebar

struct SidebarView: View {
    @Binding var selectedSection: ContentView.Section

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Logo
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(
                            LinearGradient(
                                colors: [SwiftifyTheme.kotlin, SwiftifyTheme.swift],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 44, height: 44)

                    Image(systemName: "swift")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Swiftify")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SwiftifyTheme.textPrimary)
                    Text("Kotlin → Swift")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(SwiftifyTheme.textMuted)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)

            Divider()
                .background(SwiftifyTheme.border)

            // Navigation
            VStack(spacing: 4) {
                ForEach(ContentView.Section.allCases, id: \.self) { section in
                    SidebarItem(
                        title: section.rawValue,
                        icon: iconFor(section),
                        isSelected: selectedSection == section
                    ) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedSection = section
                        }
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 16)

            Spacer()

            // Footer
            VStack(alignment: .leading, spacing: 8) {
                Divider()
                    .background(SwiftifyTheme.border)

                HStack {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundColor(SwiftifyTheme.success)
                    Text("v1.0.0")
                        .font(SwiftifyTheme.codeFontSmall)
                        .foregroundColor(SwiftifyTheme.textMuted)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
        }
        .frame(width: 220)
        .background(SwiftifyTheme.surface)
    }

    func iconFor(_ section: ContentView.Section) -> String {
        switch section {
        case .overview: return "sparkles"
        case .asyncAwait: return "arrow.triangle.2.circlepath"
        case .defaultParams: return "slider.horizontal.3"
        case .asyncStream: return "waveform.path"
        case .liveDemo: return "play.circle.fill"
        }
    }
}

struct SidebarItem: View {
    let title: String
    let icon: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(isSelected ? SwiftifyTheme.accent : SwiftifyTheme.textSecondary)
                    .frame(width: 24)

                Text(title)
                    .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? SwiftifyTheme.textPrimary : SwiftifyTheme.textSecondary)

                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? SwiftifyTheme.accent.opacity(0.15) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? SwiftifyTheme.accent.opacity(0.3) : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Overview Section

struct OverviewSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 48) {
            // Hero
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 16) {
                    LanguageBadge(name: "Kotlin", color: SwiftifyTheme.kotlin)
                    Image(systemName: "arrow.right")
                        .foregroundColor(SwiftifyTheme.textMuted)
                    LanguageBadge(name: "Swift", color: SwiftifyTheme.swift)
                }

                Text("Seamless\nKotlin Multiplatform")
                    .font(.system(size: 56, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)
                    .lineSpacing(4)

                Text("Transform Kotlin suspend functions and Flows into native Swift async/await and AsyncStream. Write idiomatic Swift code while sharing business logic with Kotlin.")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundColor(SwiftifyTheme.textSecondary)
                    .lineSpacing(6)
                    .frame(maxWidth: 600, alignment: .leading)
            }

            // Feature cards
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 20),
                GridItem(.flexible(), spacing: 20),
                GridItem(.flexible(), spacing: 20)
            ], spacing: 20) {
                FeatureCard(
                    icon: "arrow.triangle.2.circlepath",
                    title: "Async/Await",
                    description: "Kotlin suspend functions become native Swift async/await. No more completion handlers.",
                    color: SwiftifyTheme.info
                )

                FeatureCard(
                    icon: "slider.horizontal.3",
                    title: "Default Parameters",
                    description: "Convenience overloads preserve Kotlin default values in Swift.",
                    color: SwiftifyTheme.success
                )

                FeatureCard(
                    icon: "waveform.path",
                    title: "AsyncStream",
                    description: "Kotlin Flows transform to AsyncStream for reactive Swift code.",
                    color: SwiftifyTheme.accent
                )
            }

            // Code comparison preview
            VStack(alignment: .leading, spacing: 16) {
                Text("Before & After")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)

                HStack(spacing: 24) {
                    CodeBlock(
                        title: "Without Swiftify",
                        language: "Swift",
                        code: """
                        // Callback-based API
                        repository.getProducts(
                            page: 1,
                            pageSize: 20,
                            category: nil,
                            completionHandler: { result, error in
                                if let error = error {
                                    // Handle error
                                    return
                                }
                                guard let products = result else {
                                    return
                                }
                                // Use products
                            }
                        )
                        """,
                        accentColor: SwiftifyTheme.textMuted
                    )

                    CodeBlock(
                        title: "With Swiftify",
                        language: "Swift",
                        code: """
                        // Native async/await + defaults
                        let products = try await repository.getProducts()

                        // Or with parameters
                        let filtered = try await repository.getProducts(
                            page: 2,
                            category: "Electronics"
                        )

                        // Real-time updates
                        for await update in repository.cartStream {
                            updateUI(cart: update)
                        }
                        """,
                        accentColor: SwiftifyTheme.accent,
                        highlighted: true
                    )
                }
            }
        }
    }
}

struct LanguageBadge: View {
    let name: String
    let color: Color

    var body: some View {
        Text(name)
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(color.opacity(0.15))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(color.opacity(0.3), lineWidth: 1)
            )
    }
}

struct FeatureCard: View {
    let icon: String
    let title: String
    let description: String
    let color: Color

    @State private var isHovered = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ZStack {
                Circle()
                    .fill(color.opacity(0.15))
                    .frame(width: 48, height: 48)

                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(color)
            }

            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(SwiftifyTheme.textPrimary)

            Text(description)
                .font(.system(size: 14))
                .foregroundColor(SwiftifyTheme.textSecondary)
                .lineSpacing(4)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(SwiftifyTheme.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    isHovered ? color.opacity(0.5) : SwiftifyTheme.border,
                    lineWidth: 1
                )
        )
        .scaleEffect(isHovered ? 1.02 : 1.0)
        .animation(.easeInOut(duration: 0.2), value: isHovered)
        .onHover { isHovered = $0 }
    }
}

// MARK: - Code Block

struct CodeBlock: View {
    let title: String
    let language: String
    let code: String
    let accentColor: Color
    var highlighted: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(accentColor)

                Spacer()

                Text(language)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(SwiftifyTheme.textMuted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 4)
                            .fill(SwiftifyTheme.surfaceElevated)
                    )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(SwiftifyTheme.surfaceElevated)

            // Code
            ScrollView(.horizontal, showsIndicators: false) {
                Text(code)
                    .font(SwiftifyTheme.codeFont)
                    .foregroundColor(SwiftifyTheme.textPrimary.opacity(0.9))
                    .lineSpacing(6)
                    .padding(16)
            }
        }
        .background(SwiftifyTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    highlighted ? accentColor.opacity(0.5) : SwiftifyTheme.border,
                    lineWidth: highlighted ? 2 : 1
                )
        )
        .shadow(color: highlighted ? accentColor.opacity(0.2) : .clear, radius: 20)
    }
}

// MARK: - Async/Await Section

struct AsyncAwaitSection: View {
    @State private var demoOutput: [String] = []
    @State private var isRunning = false
    private let repository = UserRepository()

    var body: some View {
        VStack(alignment: .leading, spacing: 40) {
            SectionHeader(
                title: "Async/Await",
                subtitle: "Kotlin suspend functions become native Swift async/await"
            )

            // Explanation
            VStack(alignment: .leading, spacing: 16) {
                Text("Kotlin suspend functions are automatically transformed to Swift async functions. Swiftify generates the bridging code, but since Kotlin 1.8+, the compiler provides async/await natively. Swiftify adds convenience overloads for default parameters.")
                    .font(.system(size: 16))
                    .foregroundColor(SwiftifyTheme.textSecondary)
                    .lineSpacing(6)
            }

            // Code comparison
            HStack(alignment: .top, spacing: 24) {
                CodeBlock(
                    title: "Kotlin",
                    language: "Kotlin",
                    code: """
                    @SwiftAsync
                    suspend fun fetchUser(id: String): User {
                        delay(100)
                        return User(id, "John Doe", "john@example.com")
                    }

                    @SwiftAsync
                    suspend fun login(
                        username: String,
                        password: String
                    ): NetworkResult<User> {
                        // Authentication logic
                    }
                    """,
                    accentColor: SwiftifyTheme.kotlin
                )

                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(SwiftifyTheme.accent)
                    .padding(.top, 60)

                CodeBlock(
                    title: "Swift",
                    language: "Swift",
                    code: """
                    // Native async/await - no callbacks!
                    let user = try await repository.fetchUser(id: "123")
                    print("User: \\(user.name)")

                    // Works perfectly with Swift concurrency
                    let result = try await repository.login(
                        username: "john",
                        password: "secret"
                    )

                    // Error handling with try/catch
                    do {
                        let user = try await repository.fetchUser(id: "456")
                    } catch {
                        print("Failed: \\(error)")
                    }
                    """,
                    accentColor: SwiftifyTheme.swift,
                    highlighted: true
                )
            }

            // Interactive demo
            VStack(alignment: .leading, spacing: 16) {
                Text("Try It")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)

                HStack(spacing: 16) {
                    DemoButton(title: "Fetch User", icon: "person.fill", isLoading: isRunning) {
                        await runDemo {
                            let user = try await repository.fetchUser(id: "user_123")
                            return "Fetched: \(user.name) (\(user.email))"
                        }
                    }

                    DemoButton(title: "Login", icon: "arrow.right.circle.fill", isLoading: isRunning) {
                        await runDemo {
                            let result = try await repository.login(username: "john", password: "pass")
                            return "Login result: \(result)"
                        }
                    }

                    DemoButton(title: "Logout", icon: "arrow.left.circle.fill", isLoading: isRunning) {
                        await runDemo {
                            try await repository.logout()
                            return "Logged out successfully"
                        }
                    }
                }

                DemoOutput(lines: demoOutput)
            }
        }
    }

    func runDemo(_ action: @escaping () async throws -> String) async {
        isRunning = true
        demoOutput.append("⏳ Running...")
        do {
            let result = try await action()
            demoOutput.append("✅ \(result)")
        } catch {
            demoOutput.append("❌ Error: \(error.localizedDescription)")
        }
        isRunning = false
    }
}

// MARK: - Default Parameters Section

struct DefaultParamsSection: View {
    @State private var demoOutput: [String] = []
    @State private var isRunning = false
    private let repository = ProductRepository()

    var body: some View {
        VStack(alignment: .leading, spacing: 40) {
            SectionHeader(
                title: "Default Parameters",
                subtitle: "Kotlin default values preserved as Swift convenience overloads"
            )

            // Explanation
            InfoBanner(
                icon: "lightbulb.fill",
                text: "Kotlin preserves default parameters in Swift, but you need to specify all parameters when calling. Swiftify generates convenience overloads so you can call methods with just the required parameters."
            )

            // Code comparison
            HStack(alignment: .top, spacing: 24) {
                CodeBlock(
                    title: "Kotlin",
                    language: "Kotlin",
                    code: """
                    @SwiftAsync
                    suspend fun getProducts(
                        page: Int = 1,
                        pageSize: Int = 20,
                        category: String? = null
                    ): ProductPage

                    @SwiftAsync
                    suspend fun searchProducts(
                        query: String,
                        minPrice: Double = 0.0,
                        maxPrice: Double = 1000.0,
                        inStockOnly: Boolean = false
                    ): List<Product>
                    """,
                    accentColor: SwiftifyTheme.kotlin
                )

                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(SwiftifyTheme.accent)
                    .padding(.top, 60)

                CodeBlock(
                    title: "Swift (Generated by Swiftify)",
                    language: "Swift",
                    code: """
                    // Convenience overloads generated!

                    // No parameters needed
                    let products = try await repo.getProducts()

                    // Just page
                    let page2 = try await repo.getProducts(page: 2)

                    // Page and pageSize
                    let custom = try await repo.getProducts(
                        page: 1,
                        pageSize: 50
                    )

                    // All parameters (Kotlin provides this)
                    let full = try await repo.getProducts(
                        page: 1,
                        pageSize: 20,
                        category: "Electronics"
                    )
                    """,
                    accentColor: SwiftifyTheme.swift,
                    highlighted: true
                )
            }

            // Generated code explanation
            VStack(alignment: .leading, spacing: 12) {
                Text("What Swiftify Generates")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)

                CodeBlock(
                    title: "Generated Extension",
                    language: "Swift",
                    code: """
                    extension ProductRepository {
                        // Overload: no parameters
                        public func getProducts() async throws -> ProductPage {
                            return try await getProducts(page: 1, pageSize: 20, category: nil)
                        }

                        // Overload: just page
                        public func getProducts(page: Int32) async throws -> ProductPage {
                            return try await getProducts(page: page, pageSize: 20, category: nil)
                        }

                        // Overload: page + pageSize
                        public func getProducts(page: Int32, pageSize: Int32) async throws -> ProductPage {
                            return try await getProducts(page: page, pageSize: pageSize, category: nil)
                        }
                    }
                    """,
                    accentColor: SwiftifyTheme.success
                )
            }

            // Demo
            VStack(alignment: .leading, spacing: 16) {
                Text("Try It")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)

                HStack(spacing: 16) {
                    DemoButton(title: "getProducts()", icon: "square.grid.2x2", isLoading: isRunning) {
                        await runDemo {
                            let page = try await repository.getProducts()
                            return "Loaded \(page.products.count) products (page \(page.currentPage))"
                        }
                    }

                    DemoButton(title: "getProducts(page: 2)", icon: "2.circle", isLoading: isRunning) {
                        await runDemo {
                            let page = try await repository.getProducts(page: 2)
                            return "Loaded page \(page.currentPage) of \(page.totalPages)"
                        }
                    }

                    DemoButton(title: "searchProducts(\"iPhone\")", icon: "magnifyingglass", isLoading: isRunning) {
                        await runDemo {
                            let results = try await repository.searchProducts(query: "iPhone")
                            return "Found \(results.count) products"
                        }
                    }
                }

                DemoOutput(lines: demoOutput)
            }
        }
    }

    func runDemo(_ action: @escaping () async throws -> String) async {
        isRunning = true
        demoOutput.append("⏳ Running...")
        do {
            let result = try await action()
            demoOutput.append("✅ \(result)")
        } catch {
            demoOutput.append("❌ Error: \(error.localizedDescription)")
        }
        isRunning = false
    }
}

// MARK: - AsyncStream Section

struct AsyncStreamSection: View {
    @State private var streamOutput: [String] = []
    @State private var isStreaming = false
    @State private var streamTask: Task<Void, Never>?
    private let repository = ProductRepository()
    private let chatRepository = ChatRepository()

    var body: some View {
        VStack(alignment: .leading, spacing: 40) {
            SectionHeader(
                title: "AsyncStream",
                subtitle: "Kotlin Flows become native Swift AsyncStream"
            )

            // Explanation
            Text("Kotlin Flows are powerful for reactive programming, but Swift has its own concurrency model. Swiftify transforms Flows into AsyncStream, letting you use Swift's `for await` syntax for real-time updates.")
                .font(.system(size: 16))
                .foregroundColor(SwiftifyTheme.textSecondary)
                .lineSpacing(6)

            // Code comparison
            HStack(alignment: .top, spacing: 24) {
                CodeBlock(
                    title: "Kotlin",
                    language: "Kotlin",
                    code: """
                    @SwiftFlow
                    fun watchPriceChanges(productId: String): Flow<PriceUpdate> = flow {
                        while (true) {
                            delay(2000)
                            emit(PriceUpdate(
                                productId = productId,
                                oldPrice = currentPrice,
                                newPrice = newPrice
                            ))
                        }
                    }

                    @SwiftFlow
                    val cart: StateFlow<Cart>
                    """,
                    accentColor: SwiftifyTheme.kotlin
                )

                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(SwiftifyTheme.accent)
                    .padding(.top, 60)

                CodeBlock(
                    title: "Swift",
                    language: "Swift",
                    code: """
                    // Real-time price updates with for-await
                    for await update in repository.watchPriceChanges(productId: "123") {
                        print("Price changed: $\\(update.oldPrice) → $\\(update.newPrice)")
                        updatePriceLabel(update.newPrice)
                    }

                    // Cart updates as AsyncStream property
                    for await cart in repository.cartStream {
                        updateCartBadge(count: cart.items.count)
                        updateCartTotal(cart.total)
                    }
                    """,
                    accentColor: SwiftifyTheme.swift,
                    highlighted: true
                )
            }

            // What gets generated
            VStack(alignment: .leading, spacing: 12) {
                Text("Generated AsyncStream Wrapper")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(SwiftifyTheme.textPrimary)

                CodeBlock(
                    title: "Generated Extension",
                    language: "Swift",
                    code: """
                    extension ProductRepository {
                        public func watchPriceChanges(productId: String) -> AsyncStream<PriceUpdate> {
                            return AsyncStream { continuation in
                                let collector = SwiftifyFlowCollector<PriceUpdate>(
                                    onEmit: { value in continuation.yield(value) },
                                    onComplete: { continuation.finish() },
                                    onError: { _ in continuation.finish() }
                                )
                                self.watchPriceChanges(productId: productId)
                                    .collect(collector: collector, completionHandler: { _ in })
                            }
                        }

                        public var cartStream: AsyncStream<Cart> { ... }
                    }
                    """,
                    accentColor: SwiftifyTheme.success
                )
            }

            // Live demo
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Live Stream Demo")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SwiftifyTheme.textPrimary)

                    Spacer()

                    if isStreaming {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(SwiftifyTheme.accent)
                                .frame(width: 8, height: 8)
                                .opacity(isStreaming ? 1 : 0)
                                .animation(.easeInOut(duration: 0.5).repeatForever(), value: isStreaming)
                            Text("STREAMING")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(SwiftifyTheme.accent)
                        }
                    }
                }

                HStack(spacing: 16) {
                    DemoButton(
                        title: isStreaming ? "Stop Streaming" : "Watch Price Changes",
                        icon: isStreaming ? "stop.circle.fill" : "waveform.path",
                        isLoading: false,
                        color: isStreaming ? .red : SwiftifyTheme.accent
                    ) {
                        if isStreaming {
                            stopStream()
                        } else {
                            startPriceStream()
                        }
                    }

                    DemoButton(title: "Clear", icon: "trash", isLoading: false, color: SwiftifyTheme.textMuted) {
                        streamOutput.removeAll()
                    }
                }

                StreamOutput(lines: streamOutput, isLive: isStreaming)
            }
        }
    }

    func startPriceStream() {
        isStreaming = true
        streamOutput.append("🔴 Starting price stream...")

        streamTask = Task {
            var count = 0
            for await update in repository.watchPriceChanges(productId: "prod_1") {
                count += 1
                let direction = update.newPrice > update.oldPrice ? "📈" : "📉"
                streamOutput.append("\(direction) Price: $\(String(format: "%.2f", update.oldPrice)) → $\(String(format: "%.2f", update.newPrice))")

                if count >= 5 {
                    break
                }
            }
            streamOutput.append("✅ Stream completed (\(count) updates)")
            isStreaming = false
        }
    }

    func stopStream() {
        streamTask?.cancel()
        streamTask = nil
        isStreaming = false
        streamOutput.append("⏹ Stream stopped")
    }
}

// MARK: - Live Demo Section

struct LiveDemoSection: View {
    @State private var isConnected = false
    @State private var conversations: [Conversation] = []
    @State private var messages: [Message] = []
    @State private var logs: [String] = []
    @State private var isLoading = false
    @State private var messageTask: Task<Void, Never>?
    private let repository = ChatRepository()

    var body: some View {
        VStack(alignment: .leading, spacing: 40) {
            SectionHeader(
                title: "Live Chat Demo",
                subtitle: "Full working demo with all Swiftify features"
            )

            HStack(alignment: .top, spacing: 24) {
                // Chat UI
                VStack(spacing: 0) {
                    // Connection status
                    HStack {
                        Circle()
                            .fill(isConnected ? SwiftifyTheme.success : SwiftifyTheme.textMuted)
                            .frame(width: 10, height: 10)
                        Text(isConnected ? "Connected" : "Disconnected")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(isConnected ? SwiftifyTheme.success : SwiftifyTheme.textMuted)

                        Spacer()

                        HStack(spacing: 8) {
                            Button(action: { Task { await connect() } }) {
                                Image(systemName: "wifi")
                                    .foregroundColor(SwiftifyTheme.success)
                            }
                            .buttonStyle(.plain)
                            .disabled(isConnected)

                            Button(action: { Task { await disconnect() } }) {
                                Image(systemName: "wifi.slash")
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.plain)
                            .disabled(!isConnected)
                        }
                    }
                    .padding(12)
                    .background(SwiftifyTheme.surfaceElevated)

                    Divider().background(SwiftifyTheme.border)

                    // Conversations
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("Conversations")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(SwiftifyTheme.textMuted)
                            Spacer()
                            Button(action: { Task { await loadConversations() } }) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 11))
                                    .foregroundColor(SwiftifyTheme.textMuted)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)

                        if conversations.isEmpty {
                            Text("Click refresh to load")
                                .font(.system(size: 12))
                                .foregroundColor(SwiftifyTheme.textMuted)
                                .padding(12)
                        } else {
                            ForEach(conversations, id: \.id) { conv in
                                ConversationRow(conversation: conv)
                            }
                        }
                    }

                    Divider().background(SwiftifyTheme.border)

                    // Messages
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("Messages")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(SwiftifyTheme.textMuted)
                            Spacer()
                            Button(action: { Task { await loadMessages() } }) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 11))
                                    .foregroundColor(SwiftifyTheme.textMuted)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)

                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(messages.prefix(8), id: \.id) { msg in
                                    MessageBubble(message: msg)
                                }
                            }
                            .padding(12)
                        }
                        .frame(height: 200)
                    }

                    Divider().background(SwiftifyTheme.border)

                    // Actions
                    HStack(spacing: 8) {
                        Button(action: { Task { await sendMessage() } }) {
                            HStack {
                                Image(systemName: "paperplane.fill")
                                Text("Send")
                            }
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(SwiftifyTheme.accent)
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)

                        Button(action: { startWatching() }) {
                            HStack {
                                Image(systemName: "bell.fill")
                                Text("Watch Messages")
                            }
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(SwiftifyTheme.info)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(SwiftifyTheme.info.opacity(0.15))
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(12)
                }
                .frame(width: 320)
                .background(SwiftifyTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(SwiftifyTheme.border, lineWidth: 1)
                )

                // Activity log
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("Activity Log")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(SwiftifyTheme.textPrimary)

                        Spacer()

                        if isLoading {
                            ProgressView()
                                .scaleEffect(0.6)
                        }

                        Button(action: { logs.removeAll() }) {
                            Text("Clear")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(SwiftifyTheme.textMuted)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(12)
                    .background(SwiftifyTheme.surfaceElevated)

                    Divider().background(SwiftifyTheme.border)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(Array(logs.enumerated()), id: \.offset) { _, log in
                                Text(log)
                                    .font(SwiftifyTheme.codeFontSmall)
                                    .foregroundColor(logColor(for: log))
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .frame(maxWidth: .infinity)
                .background(SwiftifyTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(SwiftifyTheme.border, lineWidth: 1)
                )
            }

            // Code being executed
            CodeBlock(
                title: "Code Being Executed",
                language: "Swift",
                code: """
                // All using Swiftify-generated APIs!

                // Connect with async/await
                let state = try await repository.connect()

                // Load conversations with convenience overload
                let conversations = try await repository.getConversations()  // Uses defaults

                // Load messages with some parameters
                let messages = try await repository.getMessages(conversationId: "conv_1")

                // Send message
                let sent = try await repository.sendMessage(conversationId: "conv_1", content: "Hello!")

                // Watch for new messages with AsyncStream
                for await message in repository.watchMessages(conversationId: "conv_1") {
                    // Real-time updates!
                    messages.insert(message, at: 0)
                }
                """,
                accentColor: SwiftifyTheme.accent,
                highlighted: true
            )
        }
    }

    func log(_ message: String) {
        let timestamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        logs.insert("[\(timestamp)] \(message)", at: 0)
    }

    func logColor(for log: String) -> Color {
        if log.contains("✅") || log.contains("Success") { return SwiftifyTheme.success }
        if log.contains("❌") || log.contains("Error") { return .red }
        if log.contains("📨") || log.contains("→") { return SwiftifyTheme.info }
        return SwiftifyTheme.textSecondary
    }

    func connect() async {
        log("⏳ Connecting...")
        isLoading = true
        do {
            let state = try await repository.connect()
            isConnected = true
            log("✅ Connected: \(state)")
        } catch {
            log("❌ Error: \(error.localizedDescription)")
        }
        isLoading = false
    }

    func disconnect() async {
        log("⏳ Disconnecting...")
        isLoading = true
        do {
            try await repository.disconnect()
            isConnected = false
            log("✅ Disconnected")
        } catch {
            log("❌ Error: \(error.localizedDescription)")
        }
        isLoading = false
    }

    func loadConversations() async {
        log("⏳ Loading conversations... (using convenience overload)")
        isLoading = true
        do {
            // Using convenience overload - no parameters needed!
            conversations = try await repository.getConversations()
            log("✅ Loaded \(conversations.count) conversations")
        } catch {
            log("❌ Error: \(error.localizedDescription)")
        }
        isLoading = false
    }

    func loadMessages() async {
        log("⏳ Loading messages... (using convenience overload)")
        isLoading = true
        do {
            // Using convenience overload - just conversationId
            let page = try await repository.getMessages(conversationId: "conv_1")
            messages = page.messages
            log("✅ Loaded \(messages.count) messages")
        } catch {
            log("❌ Error: \(error.localizedDescription)")
        }
        isLoading = false
    }

    func sendMessage() async {
        log("⏳ Sending message...")
        isLoading = true
        do {
            let msg = try await repository.sendMessage(conversationId: "conv_1", content: "Hello from Swift!")
            messages.insert(msg, at: 0)
            log("✅ Sent: \(msg.content)")
        } catch {
            log("❌ Error: \(error.localizedDescription)")
        }
        isLoading = false
    }

    func startWatching() {
        log("🔴 Starting message stream (AsyncStream)...")
        messageTask?.cancel()
        messageTask = Task {
            var count = 0
            for await msg in repository.watchMessages(conversationId: "conv_1") {
                count += 1
                messages.insert(msg, at: 0)
                log("📨 New message: \(msg.content)")
                if count >= 3 { break }
            }
            log("✅ Stream completed (\(count) messages)")
        }
    }
}

// MARK: - Supporting Views

struct SectionHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 36, weight: .bold))
                .foregroundColor(SwiftifyTheme.textPrimary)

            Text(subtitle)
                .font(.system(size: 18))
                .foregroundColor(SwiftifyTheme.textSecondary)
        }
    }
}

struct InfoBanner: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(SwiftifyTheme.info)

            Text(text)
                .font(.system(size: 14))
                .foregroundColor(SwiftifyTheme.textSecondary)
                .lineSpacing(4)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SwiftifyTheme.info.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(SwiftifyTheme.info.opacity(0.3), lineWidth: 1)
        )
    }
}

struct DemoButton: View {
    let title: String
    let icon: String
    let isLoading: Bool
    var color: Color = SwiftifyTheme.accent
    let action: () async -> Void

    var body: some View {
        Button(action: { Task { await action() } }) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                } else {
                    Image(systemName: icon)
                }
                Text(title)
            }
            .font(.system(size: 13, weight: .medium))
            .foregroundColor(color)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(color.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }
}

struct DemoOutput: View {
    let lines: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(lines.suffix(5).enumerated()), id: \.offset) { _, line in
                Text(line)
                    .font(SwiftifyTheme.codeFontSmall)
                    .foregroundColor(lineColor(for: line))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 80, alignment: .topLeading)
        .background(SwiftifyTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(SwiftifyTheme.border, lineWidth: 1)
        )
    }

    func lineColor(for line: String) -> Color {
        if line.contains("✅") { return SwiftifyTheme.success }
        if line.contains("❌") { return .red }
        if line.contains("⏳") { return SwiftifyTheme.textMuted }
        return SwiftifyTheme.textSecondary
    }
}

struct StreamOutput: View {
    let lines: [String]
    let isLive: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(lines.suffix(8).enumerated()), id: \.offset) { _, line in
                Text(line)
                    .font(SwiftifyTheme.codeFontSmall)
                    .foregroundColor(lineColor(for: line))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
        .background(SwiftifyTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isLive ? SwiftifyTheme.accent : SwiftifyTheme.border, lineWidth: isLive ? 2 : 1)
        )
        .animation(.easeInOut, value: isLive)
    }

    func lineColor(for line: String) -> Color {
        if line.contains("✅") { return SwiftifyTheme.success }
        if line.contains("❌") || line.contains("⏹") { return .red }
        if line.contains("📈") { return SwiftifyTheme.success }
        if line.contains("📉") { return .red }
        if line.contains("🔴") { return SwiftifyTheme.accent }
        return SwiftifyTheme.textSecondary
    }
}

struct ConversationRow: View {
    let conversation: Conversation

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(conversation.isOnline ? SwiftifyTheme.success : SwiftifyTheme.textMuted)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(conversation.participantName)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(SwiftifyTheme.textPrimary)
                Text(conversation.lastMessage)
                    .font(.system(size: 11))
                    .foregroundColor(SwiftifyTheme.textMuted)
                    .lineLimit(1)
            }

            Spacer()

            if conversation.unreadCount > 0 {
                Text("\(conversation.unreadCount)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(SwiftifyTheme.accent)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}

struct MessageBubble: View {
    let message: Message

    var isMe: Bool { message.senderId == "me" }

    var body: some View {
        HStack {
            if isMe { Spacer() }

            Text(message.content)
                .font(.system(size: 13))
                .foregroundColor(isMe ? .white : SwiftifyTheme.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(isMe ? SwiftifyTheme.accent : SwiftifyTheme.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: 12))

            if !isMe { Spacer() }
        }
    }
}

#Preview {
    ContentView()
}
