# Swiftify

**Transform Kotlin Multiplatform APIs into idiomatic Swift**

Swiftify automatically generates Swift-friendly wrappers for your Kotlin Multiplatform code, bridging the gap between Kotlin coroutines and Swift concurrency.

## Features

| Kotlin | Swift | Status |
|--------|-------|--------|
| `suspend fun` | `async throws` | ✅ |
| `Flow<T>` | `AsyncStream<T>` | ✅ |
| `StateFlow<T>` | `AsyncStream<T>` | ✅ |
| Default parameters | Preserved | ✅ |
| Sealed classes | Swift enums | 🚧 Preview |

## Quick Start

### 1. Add the Plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.swiftify") version "0.1.0-SNAPSHOT"
}
```

### 2. Write Your Kotlin Code

```kotlin
class UserRepository {
    @SwiftAsync
    suspend fun fetchUser(id: String): User { ... }

    @SwiftFlow
    fun getUserUpdates(userId: String): Flow<User> = flow { ... }
}
```

### 3. Generate & Use

```bash
./gradlew swiftifyGenerate
```

```swift
// Swift - just works!
let user = try await repository.fetchUser(id: "123")

for await update in repository.getUserUpdates(userId: "123") {
    print("Updated: \(update.name)")
}
```

---

## Configuration Guide

Swiftify **auto-detects** your framework name from the Kotlin Multiplatform configuration - no manual setup required.

### Zero Config (Recommended)

Just apply the plugin and add annotations to your Kotlin code:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.swiftify") version "0.1.0-SNAPSHOT"
}

kotlin {
    iosArm64().binaries.framework {
        baseName = "MyKit"  // <- Swiftify auto-detects this
    }
}

// That's it! Add annotations to your code:
class MyRepository {
    @SwiftAsync
    suspend fun getData(): Data { ... }
}
```

### Custom Transformation Rules (Optional)

If you want to customize how transformations work:

```kotlin
swiftify {
    // Framework name is auto-detected - no need to set it!

    sealedClasses {
        transformToEnum(exhaustive = true)
    }
    suspendFunctions {
        transformToAsync(throwing = true)
    }
    flowTypes {
        transformToAsyncSequence()
    }
}
```

### Annotations vs DSL

| Approach | How it Works |
|----------|-------------|
| **Annotations** | Add `@SwiftAsync`, `@SwiftFlow` to specific declarations |
| **DSL Rules** | Configure global behavior for all declarations |
| **Mixed** | Use both for fine-tuned control |

```kotlin
class UserRepository {
    @SwiftAsync  // Explicitly marked - will be transformed
    suspend fun fetchUser(id: String): User

    // No annotation - NOT transformed (keeps original KMP behavior)
    suspend fun internalFetch(): Data
}
```

---

## Annotations Reference

### @SwiftAsync

Transforms a `suspend` function to Swift `async throws`.

```kotlin
@SwiftAsync
suspend fun login(username: String, password: String): AuthResult
```

**Generated Swift:**
```swift
public func login(username: String, password: String) async throws -> AuthResult {
    return try await withCheckedThrowingContinuation { continuation in
        self.login(username: username, password: password) { result, error in
            if let error = error {
                continuation.resume(throwing: error)
            } else if let result = result {
                continuation.resume(returning: result)
            }
        }
    }
}
```

### @SwiftFlow

Transforms a `Flow`-returning function or property to `AsyncStream`.

```kotlin
@SwiftFlow
fun watchMessages(chatId: String): Flow<Message>

@SwiftFlow
val connectionState: StateFlow<ConnectionState>
```

**Generated Swift:**
```swift
public func watchMessages(chatId: String) -> AsyncStream<Message> {
    return AsyncStream { continuation in
        let collector = SwiftifyFlowCollector<Message>(
            onEmit: { value in continuation.yield(value) },
            onComplete: { continuation.finish() },
            onError: { _ in continuation.finish() }
        )
        self.watchMessages(chatId: chatId).collect(collector: collector) { _ in }
    }
}

// Properties get "Stream" suffix to avoid conflicts
public var connectionStateStream: AsyncStream<ConnectionState> { ... }
```

### @SwiftEnum (Preview)

Transforms a `sealed class` to a Swift `enum`.

```kotlin
@SwiftEnum(name = "NetworkResult")
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
```

**Generated Swift:**
```swift
@frozen
public enum NetworkResult<T> {
    case success(data: T)
    case error(message: String)
    case loading
}
```

> **Note:** Sealed class transformation is currently preview-only and not included in implementation builds to avoid type conflicts with Kotlin-exported classes.

---

## Type Mappings

| Kotlin | Swift | Notes |
|--------|-------|-------|
| `String` | `String` | |
| `Int` | `Int32` | Kotlin/Native exports as Int32 |
| `Long` | `Int64` | |
| `Double` | `Double` | |
| `Float` | `Float` | |
| `Boolean` | `Bool` | |
| `List<T>` | `[T]` | |
| `T?` | `T?` | `null` → `nil` |
| `Unit` | `Void` | |

### Default Parameters

Kotlin default parameters are preserved:

```kotlin
@SwiftAsync
suspend fun getProducts(
    page: Int = 1,
    pageSize: Int = 20,
    category: String? = null
): ProductPage
```

```swift
public func getProducts(
    page: Int32 = 1,
    pageSize: Int32 = 20,
    category: String? = nil  // null → nil
) async throws -> ProductPage
```

---

## Project Structure

```
your-project/
├── src/commonMain/kotlin/
│   └── com/example/
│       └── UserRepository.kt       # Your Kotlin code with annotations
├── build/generated/swiftify/
│   ├── Swiftify.swift              # Combined generated extensions
│   ├── SwiftifyRuntime.swift       # Runtime helpers (FlowCollector, etc.)
│   └── YourFramework.apinotes      # API notes for Xcode
└── build.gradle.kts
```

---

## Gradle Tasks

| Task | Description |
|------|-------------|
| `swiftifyGenerate` | Generate Swift code |
| `swiftifyPreview` | Preview without writing files |

```bash
# Generate Swift wrappers
./gradlew swiftifyGenerate

# Preview specific class
./gradlew swiftifyPreview --class=com.example.UserRepository
```

---

## Complete Example

### Kotlin Code

```kotlin
// src/commonMain/kotlin/com/example/ChatRepository.kt
package com.example

import io.swiftify.annotations.SwiftAsync
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.flow.*

class ChatRepository {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    @SwiftFlow
    val connectionState: StateFlow<ConnectionState> = _connectionState

    @SwiftAsync
    suspend fun connect(): ConnectionState {
        _connectionState.value = ConnectionState.CONNECTING
        delay(500)
        _connectionState.value = ConnectionState.CONNECTED
        return ConnectionState.CONNECTED
    }

    @SwiftAsync
    suspend fun sendMessage(chatId: String, content: String): Message {
        delay(100)
        return Message(id = "msg_123", content = content)
    }

    @SwiftFlow
    fun watchMessages(chatId: String): Flow<Message> = flow {
        while (true) {
            delay(3000)
            emit(Message(id = "...", content = "New message"))
        }
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}
```

### Swift Usage

```swift
import SampleKit
import SwiftUI

class ChatViewModel: ObservableObject {
    @Published var isConnected = false
    @Published var messages: [Message] = []

    private let repository = ChatRepository()

    func connect() async {
        do {
            let state = try await repository.connect()
            await MainActor.run {
                isConnected = true
            }
        } catch {
            print("Failed: \(error)")
        }
    }

    func send(_ text: String) async {
        do {
            let msg = try await repository.sendMessage(chatId: "chat_1", content: text)
            await MainActor.run {
                messages.append(msg)
            }
        } catch {
            print("Send failed: \(error)")
        }
    }

    func watchMessages() async {
        for await message in repository.watchMessages(chatId: "chat_1") {
            await MainActor.run {
                messages.append(message)
            }
        }
    }
}
```

---

## Module Structure

```
swiftify/
├── swiftify-annotations/    # @SwiftAsync, @SwiftFlow, @SwiftEnum
├── swiftify-common/         # Core types (SwiftType, specs)
├── swiftify-dsl/            # Gradle DSL (swiftify { ... })
├── swiftify-analyzer/       # Kotlin source analyzer
├── swiftify-generator/      # Swift code generator
├── swiftify-linker/         # Compiler linker plugin
├── swiftify-gradle-plugin/  # Gradle plugin
├── swiftify-runtime/        # Kotlin runtime support
└── sample/                  # Demo project with macOS app
```

---

## Requirements

- Kotlin 2.0+
- Gradle 8.0+
- Xcode 15+
- macOS 13+ / iOS 16+

---

## Sample Project

The `sample/` directory contains a complete demo project with macOS and iOS apps showcasing all Swiftify features.

### Quick Start

```bash
# Run macOS demo app
./run-mac-demo.sh

# Run iOS demo app (opens Xcode)
./run-ios-demo.sh
```

### Sample Structure

```
sample/
├── src/commonMain/kotlin/com/example/
│   ├── UserRepository.kt      # Basic async/await demo
│   ├── ProductRepository.kt   # E-commerce with cart, checkout
│   ├── ChatRepository.kt      # Real-time messaging
│   └── NetworkResult.kt       # Sealed class example
├── macApp/                    # macOS SwiftUI app
│   └── macApp/
│       └── ContentView.swift  # Sidebar navigation with 3 demos
├── iosApp/                    # iOS SwiftUI app
│   └── iosApp/
│       └── ContentView.swift  # Tab navigation with 3 demos
└── build/generated/swiftify/
    ├── Swiftify.swift         # Generated Swift extensions
    └── SwiftifyRuntime.swift  # Runtime helpers
```

### Demo Features

| Demo | Features Demonstrated |
|------|----------------------|
| **User** | `@SwiftAsync` for suspend functions, `@SwiftFlow` for user updates stream |
| **E-commerce** | Default parameters, cart state, `Flow` for price watching |
| **Chat** | Connection state, real-time messages, typing indicators |

### Manual Build Steps

```bash
# 1. Build the Kotlin framework (Swift code auto-generated!)
./gradlew :sample:linkDebugFrameworkMacosArm64

# 2. Open and run in Xcode
open sample/macApp/macApp.xcodeproj
# or
open sample/iosApp/iosApp.xcodeproj
```

> **Note:** Swiftify automatically generates Swift code when you build the framework. No separate step needed!

---

## Development

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Publish to local Maven
./gradlew publishToMavenLocal

# Build framework (auto-generates Swift)
./gradlew :sample:linkDebugFrameworkMacosArm64

# Run demo apps
./run-mac-demo.sh
./run-ios-demo.sh
```

---

## License

Apache 2.0
