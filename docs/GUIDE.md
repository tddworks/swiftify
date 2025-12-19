# Swiftify Developer Guide

> Make Kotlin Multiplatform feel native in Swift

## Overview

Swiftify bridges the gap between Kotlin Multiplatform and Swift by solving two key pain points:

1. **Default Parameters** - Swift doesn't support default parameters from Kotlin/ObjC interfaces
2. **Kotlin Flow** - No native way to consume Flow as Swift AsyncStream

### What Swiftify Does

| Kotlin | Problem in Swift | Swiftify Solution |
|--------|-----------------|-------------------|
| `suspend fun getData(limit: Int = 10)` | Must specify all params | Generates convenience overloads |
| `fun watchData(): Flow<T>` | Complex FlowCollector API | Wraps as `AsyncStream<T>` |

### What Swiftify Does NOT Do

- **Async/await conversion** - Kotlin 2.0+ already exports suspend functions as `async throws`
- **Type generation** - Kotlin/Native already generates Swift types

---

## Quick Start

### 1. Add Dependencies

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
    kotlin("multiplatform") version "2.0.21"
    id("io.swiftify") version "0.1.0-SNAPSHOT"
}
```

### 2. Annotate Your Code

```kotlin
import io.swiftify.annotations.SwiftDefaults
import io.swiftify.annotations.SwiftFlow

class NotesRepository {
    @SwiftDefaults
    suspend fun getNotes(limit: Int = 10): List<Note> { ... }

    @SwiftFlow
    fun watchNotes(): Flow<List<Note>> = flow { ... }
}
```

### 3. Build

```bash
./gradlew linkReleaseFrameworkIosArm64
# or
./gradlew linkReleaseFrameworkMacosArm64
```

Swift code is automatically generated and embedded into your framework.

### 4. Use in Swift

```swift
import YourFramework

let repo = NotesRepository()

// Convenience overloads - no need to specify all params!
let notes = try await repo.getNotes()        // uses default limit=10
let five = try await repo.getNotes(limit: 5) // custom limit

// Native AsyncStream - clean for-await syntax
for await notes in repo.watchNotes() {
    print("Got \(notes.count) notes")
}
```

---

## Annotations

### @SwiftDefaults

Generates Swift convenience overloads for functions with default parameters.

**When to use:** Any function (suspend or regular) with default parameters that you want to call from Swift without specifying all arguments.

```kotlin
@SwiftDefaults
suspend fun search(
    query: String,
    page: Int = 1,
    limit: Int = 20,
    includeArchived: Boolean = false
): SearchResult
```

**Generated Swift:**

```swift
extension SearchRepository {
    // Just query (uses all defaults)
    func search(query: String) async throws -> SearchResult

    // Query + page
    func search(query: String, page: Int32) async throws -> SearchResult

    // Query + page + limit
    func search(query: String, page: Int32, limit: Int32) async throws -> SearchResult

    // Full signature provided by Kotlin/Native
}
```

**Best practices:**
- Place required parameters first, optional parameters last
- Use meaningful default values
- Limit to 3-4 default parameters to avoid overload explosion

---

### @SwiftFlow

Wraps Kotlin Flow as Swift AsyncStream for native `for await` consumption.

**When to use:** Any function returning `Flow<T>`, `StateFlow<T>`, or `SharedFlow<T>`.

```kotlin
@SwiftFlow
fun watchMessages(chatId: String): Flow<Message>

@SwiftFlow
val connectionState: StateFlow<ConnectionState>
```

**Generated Swift:**

```swift
extension ChatRepository {
    // Function returns AsyncStream
    func watchMessages(chatId: String) -> AsyncStream<Message>

    // Property gets "Stream" suffix to avoid conflicts
    var connectionStateStream: AsyncStream<ConnectionState>
}
```

**Usage in Swift:**

```swift
// Consume with for-await
for await message in repo.watchMessages(chatId: "123") {
    print("New message: \(message.content)")
}

// Or with Task
Task {
    for await state in repo.connectionStateStream {
        updateUI(state)
    }
}
```

**Best practices:**
- Use `@SwiftFlow` for all Flow-returning APIs consumed by Swift
- StateFlow properties get `Stream` suffix automatically
- Remember to handle cancellation in Swift (Task cancellation propagates)

---

## Configuration

### Zero Configuration (Recommended)

Swiftify auto-detects your framework name from the Kotlin Multiplatform configuration:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.swiftify")
}

kotlin {
    iosArm64().binaries.framework {
        baseName = "MyKit"  // Auto-detected!
    }
}
```

### Optional DSL Configuration

For advanced customization:

```kotlin
swiftify {
    // Sealed class → Swift enum transformation
    sealedClasses {
        transformToEnum(exhaustive = true)
    }

    // Default parameter convenience overload generation
    defaultParameters {
        generateOverloads(maxOverloads = 5)
    }

    // Flow → AsyncStream transformation
    flowTypes {
        transformToAsyncStream()
    }
}
```

> **Note:** Kotlin 2.0+ already exports suspend functions as Swift async/await automatically.
> The `defaultParameters` block only controls generation of convenience overloads for functions
> with default parameters.

### Defaults

```kotlin
swiftify {
    defaults {
        requireAnnotations = true            // Only process annotated functions (default)
        generateDefaultOverloads = true      // Generate convenience overloads
        transformFlowToAsyncStream = true    // Wrap Flow as AsyncStream
        transformSealedClassesToEnums = true // Transform sealed classes
        maxDefaultOverloads = 5              // Limit overload combinations
    }
}
```

### Annotation Mode vs DSL Mode

Swiftify supports two modes of operation:

**Annotation Mode (default)**: Only process functions explicitly marked with `@SwiftDefaults` or `@SwiftFlow`.

```kotlin
// Only this function will be processed
@SwiftDefaults
suspend fun getNotes(limit: Int = 10): List<Note>

// This function is NOT processed (no annotation)
suspend fun internalFetch(): Data
```

**DSL Mode**: Process ALL matching functions without requiring annotations.

```kotlin
swiftify {
    defaults {
        requireAnnotations = false  // Process all suspend/Flow functions
    }
}
```

Use DSL mode when:
- You want to transform your entire API without adding annotations
- You're migrating an existing codebase
- You prefer configuration over annotations

---

## Project Structure

After building, Swiftify generates these files in `build/generated/swiftify/`:

```
build/generated/swiftify/
├── Swiftify.swift           # Combined extensions for all annotated declarations
├── SwiftifyRuntime.swift    # Swift helpers (FlowCollector for AsyncStream)
└── YourFramework.apinotes   # API notes for Xcode
```

These files are automatically embedded into your framework during the link phase.

> **Note:** All runtime support is pure Swift - no Kotlin bridge code is needed since Kotlin 2.0+ natively exports suspend functions as Swift async/await.

---

## Type Mappings

| Kotlin | Swift | Notes |
|--------|-------|-------|
| `String` | `String` | Direct mapping |
| `Int` | `Int32` | Kotlin/Native uses 32-bit |
| `Long` | `Int64` | |
| `Double` | `Double` | |
| `Float` | `Float` | |
| `Boolean` | `Bool` | |
| `List<T>` | `[T]` | |
| `Map<K,V>` | `[K: V]` | |
| `T?` | `T?` | `null` → `nil` |
| `Unit` | `Void` | |
| `Flow<T>` | `AsyncStream<T>` | With `@SwiftFlow` |

---

## Common Patterns

### Repository Pattern

```kotlin
class UserRepository {
    // CRUD operations with defaults
    @SwiftDefaults
    suspend fun getUsers(page: Int = 1, limit: Int = 20): List<User>

    @SwiftDefaults
    suspend fun getUser(id: String): User?

    @SwiftDefaults
    suspend fun createUser(name: String, email: String, role: Role = Role.USER): User

    // Real-time updates
    @SwiftFlow
    fun watchUser(id: String): Flow<User?>

    @SwiftFlow
    val currentUser: StateFlow<User?>
}
```

**Swift usage:**

```swift
let repo = UserRepository()

// Simple calls
let users = try await repo.getUsers()
let admins = try await repo.getUsers(limit: 100)

// Real-time
for await user in repo.watchUser(id: "123") {
    updateProfile(user)
}
```

### Pagination

```kotlin
@SwiftDefaults
suspend fun getProducts(
    page: Int = 1,
    pageSize: Int = 20,
    category: String? = nil,
    sortBy: SortOrder = SortOrder.NEWEST
): ProductPage
```

**Swift:**

```swift
// First page, default size
let page1 = try await repo.getProducts()

// Custom pagination
let page2 = try await repo.getProducts(page: 2)

// With filters
let electronics = try await repo.getProducts(page: 1, pageSize: 50, category: "electronics")
```

### Real-time Features

```kotlin
class ChatRepository {
    @SwiftFlow
    val connectionState: StateFlow<ConnectionState>

    @SwiftFlow
    fun watchMessages(chatId: String): Flow<Message>

    @SwiftFlow
    fun watchTypingIndicators(chatId: String): Flow<TypingStatus>
}
```

**Swift:**

```swift
// Multiple streams in parallel
Task {
    for await state in repo.connectionStateStream {
        updateConnectionBadge(state)
    }
}

Task {
    for await message in repo.watchMessages(chatId: currentChat) {
        appendMessage(message)
    }
}
```

---

## Troubleshooting

### "Duplicate symbol" errors

**Cause:** Old generated files conflicting with new ones.

**Solution:** Clean and rebuild:
```bash
rm -rf build/
./gradlew linkReleaseFrameworkMacosArm64
```

### Generated overloads not appearing in Xcode

**Cause:** Xcode caches framework headers.

**Solution:**
1. Clean Xcode build folder (Cmd+Shift+K)
2. Rebuild Kotlin framework
3. Rebuild Xcode project

### "Unresolved reference" for annotations

**Cause:** Missing annotation dependency.

**Solution:** Ensure your module depends on swiftify-annotations:
```kotlin
commonMain.dependencies {
    implementation("io.swiftify:swiftify-annotations:0.1.0-SNAPSHOT")
}
```

### Flow not converting to AsyncStream

**Cause:** Missing `@SwiftFlow` annotation.

**Solution:** Add the annotation:
```kotlin
@SwiftFlow  // Don't forget this!
fun watchData(): Flow<Data>
```

---

## Best Practices

### 1. Annotate Intentionally

Only annotate functions that will be called from Swift. Internal Kotlin functions don't need Swiftify annotations.

```kotlin
// Public API - annotate
@SwiftDefaults
suspend fun fetchUser(id: String, includeProfile: Boolean = true): User

// Internal - no annotation needed
internal suspend fun fetchUserFromCache(id: String): User?
```

### 2. Design for Swift Callers

Think about how Swift developers will call your API:

```kotlin
// Good: Required params first, optional last
@SwiftDefaults
suspend fun search(query: String, limit: Int = 20, offset: Int = 0)

// Avoid: Optional params mixed with required
@SwiftDefaults
suspend fun search(limit: Int = 20, query: String, offset: Int = 0)
```

### 3. Use Meaningful Defaults

```kotlin
// Good: Sensible defaults
@SwiftDefaults
suspend fun getProducts(page: Int = 1, pageSize: Int = 20)

// Avoid: Arbitrary defaults
@SwiftDefaults
suspend fun getProducts(page: Int = 0, pageSize: Int = 100)
```

### 4. Document Your API

```kotlin
/**
 * Fetches paginated list of products.
 *
 * @param page Page number (1-indexed)
 * @param pageSize Items per page (max 100)
 * @param category Optional category filter
 */
@SwiftDefaults
suspend fun getProducts(
    page: Int = 1,
    pageSize: Int = 20,
    category: String? = null
): ProductPage
```

### 5. Test from Swift

Always test your API from actual Swift code to ensure:
- Overloads work as expected
- AsyncStream behaves correctly
- Error handling works properly

---

## Migration from @SwiftAsync

If you were using the deprecated `@SwiftAsync` annotation:

```kotlin
// Before (deprecated)
@SwiftAsync
suspend fun getData(): Data

// After
@SwiftDefaults
suspend fun getData(): Data
```

The `@SwiftAsync` annotation is deprecated because Kotlin 2.0+ automatically exports suspend functions as Swift `async throws`. Use `@SwiftDefaults` when you have default parameters that need convenience overloads.

---

## Requirements

- Kotlin 2.0+
- Gradle 8.0+
- Xcode 15+
- macOS 13+ / iOS 16+

---

## Support

- GitHub Issues: https://github.com/anthropics/swiftify/issues
- Documentation: https://github.com/anthropics/swiftify/docs
