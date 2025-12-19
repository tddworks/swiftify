import SwiftUI
import SampleKit

// MARK: - Design System

extension Color {
    // Dark theme palette
    static let bgPrimary = Color(red: 0.08, green: 0.08, blue: 0.10)
    static let bgSecondary = Color(red: 0.11, green: 0.11, blue: 0.14)
    static let bgTertiary = Color(red: 0.14, green: 0.14, blue: 0.18)

    // Accent colors
    static let accentCoral = Color(red: 1.0, green: 0.45, blue: 0.40)
    static let accentTeal = Color(red: 0.30, green: 0.85, blue: 0.75)
    static let accentPurple = Color(red: 0.65, green: 0.50, blue: 1.0)

    // Text colors
    static let textPrimary = Color(white: 0.95)
    static let textSecondary = Color(white: 0.55)
    static let textMuted = Color(white: 0.35)
}

enum DemoCase: String, CaseIterable {
    case asyncAwait = "async/await"
    case flow = "AsyncStream"

    var icon: String {
        switch self {
        case .asyncAwait: return "bolt.fill"
        case .flow: return "arrow.triangle.2.circlepath"
        }
    }

    var subtitle: String {
        switch self {
        case .asyncAwait: return "Suspend Functions"
        case .flow: return "Kotlin Flow"
        }
    }
}

// MARK: - Main View

struct ContentView: View {
    @State private var selectedCase: DemoCase = .asyncAwait
    @State private var result: String = ""
    @State private var isRunning = false
    @State private var flowTask: Task<Void, Never>?
    @State private var showContent = false

    private let repo = NotesRepository()

    var body: some View {
        ZStack {
            // Background
            Color.bgPrimary
                .ignoresSafeArea()

            // Subtle gradient overlay
            LinearGradient(
                colors: [
                    Color.accentPurple.opacity(0.03),
                    Color.clear,
                    Color.accentTeal.opacity(0.02)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                headerView

                // Main content
                HStack(spacing: 0) {
                    // Before section
                    beforeSection
                        .frame(maxWidth: .infinity)

                    // Transformation arrow
                    transformArrow
                        .frame(width: 100)

                    // After section
                    afterSection
                        .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 40)
                .padding(.vertical, 32)

                Spacer(minLength: 0)
            }
        }
        .frame(minWidth: 900, minHeight: 520)
        .preferredColorScheme(.dark)
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                showContent = true
            }
        }
        .onChange(of: selectedCase) { _ in
            stopFlow()
            result = ""
        }
    }

    // MARK: - Header

    private var headerView: some View {
        HStack(spacing: 16) {
            // Logo
            HStack(spacing: 10) {
                Image(systemName: "swift")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.accentTeal, .accentPurple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Text("Swiftify")
                    .font(.system(size: 20, weight: .semibold, design: .rounded))
                    .foregroundColor(.textPrimary)

                Text("KMP → Swift")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundColor(.textMuted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.bgTertiary)
                    .cornerRadius(4)
            }

            Spacer()

            // Case selector
            HStack(spacing: 4) {
                ForEach(DemoCase.allCases, id: \.self) { demoCase in
                    casePill(demoCase)
                }
            }
            .padding(4)
            .background(Color.bgSecondary)
            .cornerRadius(10)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .background(
            Color.bgSecondary.opacity(0.5)
                .background(.ultraThinMaterial)
        )
    }

    private func casePill(_ demoCase: DemoCase) -> some View {
        let isSelected = selectedCase == demoCase

        return Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                selectedCase = demoCase
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: demoCase.icon)
                    .font(.system(size: 11, weight: .semibold))

                VStack(alignment: .leading, spacing: 1) {
                    Text(demoCase.rawValue)
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    Text(demoCase.subtitle)
                        .font(.system(size: 9, weight: .medium))
                        .opacity(0.7)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .foregroundColor(isSelected ? .bgPrimary : .textSecondary)
            .background(
                Group {
                    if isSelected {
                        LinearGradient(
                            colors: [.accentTeal, .accentTeal.opacity(0.8)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    } else {
                        Color.clear
                    }
                }
            )
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Before Section

    private var beforeSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Section header
            HStack(spacing: 8) {
                Circle()
                    .fill(Color.accentCoral.opacity(0.2))
                    .frame(width: 28, height: 28)
                    .overlay(
                        Image(systemName: "xmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.accentCoral)
                    )

                Text("Before")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.textPrimary)

                Text("The Problem")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.textMuted)
            }

            // Kotlin code
            VStack(alignment: .leading, spacing: 8) {
                codeLabel("Kotlin", color: .accentPurple)
                codeBlock(kotlinCode, style: .neutral)
            }

            // Swift before code
            VStack(alignment: .leading, spacing: 8) {
                codeLabel("Swift (vanilla)", color: .accentCoral)
                codeBlock(swiftBeforeCode, style: .bad)
            }

            Spacer(minLength: 0)
        }
        .opacity(showContent ? 1 : 0)
        .offset(x: showContent ? 0 : -20)
    }

    // MARK: - Transform Arrow

    private var transformArrow: some View {
        VStack(spacing: 12) {
            Spacer()

            ZStack {
                // Glow
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.accentTeal.opacity(0.3),
                                Color.clear
                            ],
                            center: .center,
                            startRadius: 0,
                            endRadius: 40
                        )
                    )
                    .frame(width: 80, height: 80)

                // Arrow circle
                Circle()
                    .fill(Color.bgTertiary)
                    .frame(width: 48, height: 48)
                    .overlay(
                        Circle()
                            .stroke(
                                LinearGradient(
                                    colors: [.accentTeal, .accentPurple],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 2
                            )
                    )
                    .overlay(
                        Image(systemName: "arrow.right")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [.accentTeal, .accentPurple],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                    )
            }

            Text("Swiftify")
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(.textMuted)
                .textCase(.uppercase)
                .tracking(2)

            Spacer()
        }
        .opacity(showContent ? 1 : 0)
        .scaleEffect(showContent ? 1 : 0.8)
    }

    // MARK: - After Section

    private var afterSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Section header
            HStack(spacing: 8) {
                Circle()
                    .fill(Color.accentTeal.opacity(0.2))
                    .frame(width: 28, height: 28)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.accentTeal)
                    )

                Text("After")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.textPrimary)

                Text("Clean & Native")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.textMuted)
            }

            // Swift after code
            VStack(alignment: .leading, spacing: 8) {
                codeLabel("Swift (with Swiftify)", color: .accentTeal)
                codeBlock(swiftAfterCode, style: .good)
            }

            Spacer(minLength: 16)

            // Run demo section
            VStack(alignment: .leading, spacing: 12) {
                Text("Try it live")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.textMuted)
                    .textCase(.uppercase)
                    .tracking(1)

                HStack(spacing: 12) {
                    // Run button
                    Button {
                        runDemo()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: isRunning ? "circle.dotted" : "play.fill")
                                .font(.system(size: 12, weight: .semibold))
                                .rotationEffect(.degrees(isRunning ? 360 : 0))
                                .animation(
                                    isRunning ? .linear(duration: 1).repeatForever(autoreverses: false) : .default,
                                    value: isRunning
                                )

                            Text(buttonText)
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        }
                        .foregroundColor(.bgPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            LinearGradient(
                                colors: [.accentTeal, .accentTeal.opacity(0.85)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .cornerRadius(8)
                        .shadow(color: .accentTeal.opacity(0.3), radius: 8, y: 4)
                    }
                    .buttonStyle(.plain)
                    .disabled(isRunning)
                    .opacity(isRunning ? 0.7 : 1)

                    // Stop button for flow
                    if selectedCase == .flow && isRunning {
                        Button {
                            stopFlow()
                        } label: {
                            Text("Stop")
                                .font(.system(size: 12, weight: .medium, design: .monospaced))
                                .foregroundColor(.textSecondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(Color.bgTertiary)
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)
                    }

                    // Result
                    if !result.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.accentTeal)
                            Text(result)
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                                .foregroundColor(.accentTeal)
                        }
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                    }
                }
            }

            Spacer(minLength: 0)
        }
        .opacity(showContent ? 1 : 0)
        .offset(x: showContent ? 0 : 20)
    }

    // MARK: - Code Components

    private func codeLabel(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold, design: .monospaced))
            .foregroundColor(color)
            .textCase(.uppercase)
            .tracking(0.5)
    }

    private enum CodeStyle {
        case neutral, bad, good
    }

    private func codeBlock(_ code: String, style: CodeStyle) -> some View {
        let bgColor: Color
        let borderColor: Color
        let glowColor: Color?

        switch style {
        case .neutral:
            bgColor = Color.bgSecondary
            borderColor = Color.textMuted.opacity(0.2)
            glowColor = nil
        case .bad:
            bgColor = Color.accentCoral.opacity(0.05)
            borderColor = Color.accentCoral.opacity(0.2)
            glowColor = nil
        case .good:
            bgColor = Color.accentTeal.opacity(0.08)
            borderColor = Color.accentTeal.opacity(0.4)
            glowColor = Color.accentTeal
        }

        return Text(code)
            .font(.system(size: 11, weight: .regular, design: .monospaced))
            .foregroundColor(.textPrimary.opacity(0.9))
            .lineSpacing(4)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(bgColor)
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(borderColor, lineWidth: 1)
            )
            .shadow(color: glowColor?.opacity(0.15) ?? .clear, radius: 12, y: 4)
    }

    // MARK: - Code Examples

    var kotlinCode: String {
        switch selectedCase {
        case .asyncAwait:
            return """
            @SwiftAsync
            suspend fun getNotes(
                limit: Int = 10
            ): List<Note>
            """
        case .flow:
            return """
            @SwiftFlow
            fun watchNote(
                id: String
            ): Flow<Note?>
            """
        }
    }

    var swiftBeforeCode: String {
        switch selectedCase {
        case .asyncAwait:
            return """
            // Callback hell, no default params
            repo.getNotes(
                limit: 10, includeArchived: false,
                completionHandler: { result, error in
                    if let error = error {
                        print("Error: \\(error)")
                        return
                    }
                    guard let notes = result else { return }
                    // finally use notes...
                }
            )
            """
        case .flow:
            return """
            // Complex FlowCollector protocol
            class MyCollector: FlowCollector {
                func emit(value: Any?,
                          completion: ...) {
                    // handle each value
                }
            }
            repo.watchNote(id: "1")
                .collect(collector: MyCollector(), ...)
            """
        }
    }

    var swiftAfterCode: String {
        switch selectedCase {
        case .asyncAwait:
            return """
            // Clean async/await + default params
            let notes = try await repo.getNotes()

            // Or with custom limit
            let notes = try await repo.getNotes(limit: 5)
            """
        case .flow:
            return """
            // Native Swift AsyncStream
            for await note in repo.watchNote(id: "1") {
                print("Updated: \\(note.title)")
            }
            """
        }
    }

    var buttonText: String {
        switch selectedCase {
        case .asyncAwait: return "getNotes()"
        case .flow: return "watchNote()"
        }
    }

    // MARK: - Demo Actions

    func runDemo() {
        withAnimation(.spring(response: 0.3)) {
            isRunning = true
            result = ""
        }

        switch selectedCase {
        case .asyncAwait:
            Task {
                do {
                    let notes = try await repo.getNotes()
                    withAnimation(.spring(response: 0.3)) {
                        result = "Got \(notes.count) notes"
                    }
                } catch {
                    withAnimation {
                        result = "Error: \(error)"
                    }
                }
                withAnimation {
                    isRunning = false
                }
            }

        case .flow:
            flowTask = Task {
                var count = 0
                for await note in repo.watchNote(id: "1") {
                    count += 1
                    withAnimation(.spring(response: 0.3)) {
                        result = "Update #\(count): \(note.title)"
                    }
                    if count >= 5 {
                        break
                    }
                }
                withAnimation {
                    isRunning = false
                }
            }
        }
    }

    func stopFlow() {
        flowTask?.cancel()
        flowTask = nil
        withAnimation {
            isRunning = false
        }
    }
}

#Preview {
    ContentView()
}
