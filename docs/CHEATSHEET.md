# Swiftify Cheatsheet

Quick reference for common patterns.

---

## Annotations

```kotlin
@SwiftDefaults    // Generate convenience overloads for default params
@SwiftFlow        // Wrap Flow as AsyncStream
@SwiftifyIgnore   // Exclude from processing
```

---

## @SwiftDefaults

```kotlin
// Kotlin
@SwiftDefaults
suspend fun fetch(id: String, limit: Int = 10): List<Item>
```

```swift
// Generated Swift
func fetch(id: String) async throws -> [Item]           // uses default
func fetch(id: String, limit: Int32) async throws -> [Item]  // full
```

---

## @SwiftFlow

```kotlin
// Kotlin
@SwiftFlow
fun watch(id: String): Flow<Item>

@SwiftFlow
val items: StateFlow<List<Item>>  // Property gets "Stream" suffix
```

```swift
// Generated Swift
func watch(id: String) -> AsyncStream<Item>
var itemsStream: AsyncStream<[Item]>
```

---

## Swift Usage Patterns

### Async/Await

```swift
// Simple call
let items = try await repo.fetch(id: "123")

// With custom params
let items = try await repo.fetch(id: "123", limit: 5)
```

### AsyncStream

```swift
// For-await loop
for await item in repo.watch(id: "123") {
    print(item)
}

// With Task
Task {
    for await items in repo.itemsStream {
        updateUI(items)
    }
}
```

---

## DSL Configuration

```kotlin
swiftify {
    defaultParameters {
        generateOverloads(maxOverloads = 5)
    }
    flowTypes {
        transformToAsyncStream()
    }
    sealedClasses {
        transformToEnum(exhaustive = true)
    }
}
```

---

## Type Mappings

| Kotlin | Swift |
|--------|-------|
| `Int` | `Int32` |
| `Long` | `Int64` |
| `String` | `String` |
| `Boolean` | `Bool` |
| `List<T>` | `[T]` |
| `Map<K,V>` | `[K:V]` |
| `T?` | `T?` |
| `Flow<T>` | `AsyncStream<T>` |

---

## Build Commands

```bash
# Build iOS framework
./gradlew linkReleaseFrameworkIosArm64

# Build macOS framework
./gradlew linkReleaseFrameworkMacosArm64

# Clean rebuild
rm -rf build/ && ./gradlew linkReleaseFrameworkMacosArm64

# Run tests
./gradlew test
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Duplicate symbols | Clean build directory |
| Missing overloads | Add `@SwiftDefaults` annotation |
| Flow not working | Add `@SwiftFlow` annotation |
| Xcode cache | Clean build folder (Cmd+Shift+K) |
