# Swiftify

**Make Kotlin Multiplatform feel native in Swift**

Swiftify enhances Kotlin Multiplatform's Swift interop by generating convenience overloads for default parameters and converting Kotlin Flow to native Swift AsyncStream.

## The Problem

Kotlin 2.0+ exports `suspend` functions as Swift `async throws` automatically. But two pain points remain:

```kotlin
// Kotlin - nice default parameters
suspend fun getNotes(limit: Int = 10, includeArchived: Boolean = false): List<Note>
```

```swift
// Swift without Swiftify - must specify ALL parameters
let notes = try await repo.getNotes(limit: 10, includeArchived: false)

// Kotlin Flow - requires complex FlowCollector protocol
class MyCollector: Kotlinx_coroutines_coreFlowCollector { ... }
repo.watchNote(id: "1").collect(collector: MyCollector()) { _ in }
```

## The Solution

```swift
// Swift with Swiftify - convenience overloads!
let notes = try await repo.getNotes()           // uses defaults
let notes = try await repo.getNotes(limit: 5)   // partial defaults

// Kotlin Flow → native AsyncStream
for await note in repo.watchNote(id: "1") {
    print("Updated: \(note.title)")
}
```

## Features

| Kotlin | Swift | What Swiftify Does |
|--------|-------|-------------------|
| `suspend fun` with defaults | `async throws` | Generates convenience overloads |
| `Flow<T>` | `AsyncStream<T>` | Wraps with native Swift API |
| `StateFlow<T>` | `AsyncStream<T>` | Adds `*Stream` property |
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

### 2. Annotate Your Kotlin Code

```kotlin
class NotesRepository {
    @SwiftDefaults
    suspend fun getNotes(limit: Int = 10): List<Note> { ... }

    @SwiftFlow
    fun watchNote(id: String): Flow<Note?> = flow { ... }
}
```

### 3. Build & Use

```bash
./gradlew linkDebugFrameworkMacosArm64  # Swift code auto-generated!
```

```swift
// Swift - clean and native!
let notes = try await repo.getNotes()        // default limit
let five = try await repo.getNotes(limit: 5) // custom limit

for await note in repo.watchNote(id: "1") {
    print("Note: \(note.title)")
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
    @SwiftDefaults
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
    defaultParameters {
        generateOverloads(maxOverloads = 5)
    }
    flowTypes {
        transformToAsyncStream()
    }
}
```

### Annotations vs DSL

| Approach | How it Works |
|----------|-------------|
| **Annotations** | Add `@SwiftDefaults`, `@SwiftFlow` to specific declarations |
| **DSL Rules** | Configure global behavior for all declarations |
| **Mixed** | Use both for fine-tuned control |

```kotlin
class UserRepository {
    @SwiftDefaults  // Generates convenience overloads for defaults
    suspend fun fetchUser(id: String, includeProfile: Boolean = true): User

    // No annotation - uses Kotlin/Native's default behavior
    suspend fun internalFetch(): Data
}
```

---

## Annotations Reference

### @SwiftDefaults

Generates **convenience overloads** for functions with default parameters.

Swift doesn't support default parameters from Kotlin/Objective-C interfaces. This annotation generates overloaded methods that call through with default values.

```kotlin
@SwiftDefaults
suspend fun getNotes(
    limit: Int = 10,
    includeArchived: Boolean = false
): List<Note>
```

**Generated Swift (convenience overloads):**
```swift
extension NotesRepository {
    // Overload 1: no parameters (uses all defaults)
    public func getNotes() async throws -> [Note] {
        return try await getNotes(limit: 10, includeArchived: false)
    }

    // Overload 2: just limit (uses default for includeArchived)
    public func getNotes(limit: Int32) async throws -> [Note] {
        return try await getNotes(limit: limit, includeArchived: false)
    }

    // Full signature already provided by Kotlin/Native
}
```

Works with both suspend and regular functions.

### @SwiftFlow

Wraps Kotlin `Flow` with native Swift `AsyncStream` for clean `for await` syntax.

```kotlin
@SwiftFlow
fun watchNote(id: String): Flow<Note?>

@SwiftFlow
val connectionState: StateFlow<ConnectionState>
```

**Generated Swift:**
```swift
extension NotesRepository {
    public func watchNote(id: String) -> AsyncStream<Note> {
        return AsyncStream { continuation in
            let collector = SwiftifyFlowCollector<Note>(
                onEmit: { value in continuation.yield(value) },
                onComplete: { continuation.finish() },
                onError: { _ in continuation.finish() }
            )
            self.watchNote(id: id).collect(collector: collector) { _ in }
        }
    }
}

// StateFlow properties get "Stream" suffix to avoid naming conflicts
public var connectionStateStream: AsyncStream<ConnectionState> { ... }
```

**Usage:**
```swift
// Clean for-await loop instead of FlowCollector
for await note in repo.watchNote(id: "1") {
    print("Note updated: \(note.title)")
}
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

Swift doesn't support default parameters from Objective-C/Kotlin interfaces. Use `@SwiftDefaults` to generate **convenience overloads**:

```kotlin
@SwiftDefaults
suspend fun getProducts(
    page: Int = 1,
    pageSize: Int = 20,
    category: String? = null
): ProductPage
```

**Generated Swift overloads:**
```swift
extension ProductRepository {
    // No params - uses all defaults
    func getProducts() async throws -> ProductPage {
        return try await getProducts(page: 1, pageSize: 20, category: nil)
    }

    // Just page
    func getProducts(page: Int32) async throws -> ProductPage {
        return try await getProducts(page: page, pageSize: 20, category: nil)
    }

    // Page + pageSize
    func getProducts(page: Int32, pageSize: Int32) async throws -> ProductPage {
        return try await getProducts(page: page, pageSize: pageSize, category: nil)
    }

    // Full signature provided by Kotlin/Native
}
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
// src/commonMain/kotlin/com/example/NotesRepository.kt
package com.example

import io.swiftify.annotations.SwiftDefaults
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class NotesRepository {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())

    // @SwiftDefaults with default parameters
    // Generates: getNotes(), getNotes(limit:)
    @SwiftDefaults
    suspend fun getNotes(
        limit: Int = 10,
        includeArchived: Boolean = false
    ): List<Note> {
        delay(100)
        return _notes.value.take(limit)
    }

    // @SwiftDefaults with multiple defaults
    // Generates: createNote(title:), createNote(title:, content:)
    @SwiftDefaults
    suspend fun createNote(
        title: String,
        content: String = "",
        pinned: Boolean = false
    ): Note {
        val note = Note(id = "note_1", title = title, content = content)
        _notes.value = listOf(note) + _notes.value
        return note
    }

    // @SwiftFlow - converts to AsyncStream
    @SwiftFlow
    fun watchNote(id: String): Flow<Note?> = flow {
        while (true) {
            emit(_notes.value.find { it.id == id })
            delay(1000)
        }
    }
}

data class Note(
    val id: String,
    val title: String,
    val content: String = ""
)
```

### Swift Usage

```swift
import SampleKit

// With Swiftify - clean and native!
let repo = NotesRepository()

// Convenience overloads for default parameters
let notes = try await repo.getNotes()           // uses defaults
let five = try await repo.getNotes(limit: 5)    // partial defaults

// Create with defaults
let note = try await repo.createNote(title: "Hello")
let note2 = try await repo.createNote(title: "Hello", content: "World")

// Flow → AsyncStream with for-await
for await note in repo.watchNote(id: "1") {
    print("Updated: \(note.title)")
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

The `sample/` directory contains a demo project showcasing Swiftify's two main features with an interactive before/after comparison.

### Quick Start

```bash
# Build and run macOS demo
./gradlew :sample:linkReleaseFrameworkMacosArm64
open sample/macApp/macApp.xcodeproj
```

### Demo App Features

The demo app shows a **before/after comparison** for each Swiftify feature:

| Feature | Before (Without Swiftify) | After (With Swiftify) |
|---------|---------------------------|----------------------|
| **async/await** | Must specify all parameters | Convenience overloads with defaults |
| **AsyncStream** | Complex FlowCollector protocol | Native `for await` syntax |

Each feature includes a **"Try it live"** button that executes the actual Swiftify-generated code.

### Sample Structure

```
sample/
├── src/commonMain/kotlin/com/example/
│   ├── NotesRepository.kt     # Primary demo (getNotes, watchNote)
│   ├── UserRepository.kt      # User management examples
│   ├── ProductRepository.kt   # E-commerce examples
│   └── ChatRepository.kt      # Real-time messaging
├── macApp/                    # macOS SwiftUI demo app
│   └── macApp/
│       └── ContentView.swift  # Before/after comparison UI
└── build/generated/swiftify/
    ├── Swiftify.swift         # Combined generated extensions
    ├── SwiftifyRuntime.swift  # FlowCollector helper
    └── SampleKit.apinotes     # API notes for Xcode
```

### Build Steps

```bash
# 1. Build the Kotlin framework (Swift code auto-generated!)
./gradlew :sample:linkReleaseFrameworkMacosArm64

# 2. Open and run in Xcode
open sample/macApp/macApp.xcodeproj
```

> **Note:** Swiftify generates Swift extensions automatically when building the framework.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Developer Guide](docs/GUIDE.md) | Comprehensive guide with examples and best practices |
| [Cheatsheet](docs/CHEATSHEET.md) | Quick reference for common patterns |
| [Architecture](docs/ARCHITECTURE.md) | Internal design and module structure |

---

## Development

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Publish to local Maven
./gradlew publishToMavenLocal

# Build sample framework (auto-generates Swift)
./gradlew :sample:linkReleaseFrameworkMacosArm64

# Open demo app in Xcode
open sample/macApp/macApp.xcodeproj
```

---

## License

Apache 2.0
