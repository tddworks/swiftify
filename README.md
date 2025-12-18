# Swiftify

**Kotlin-to-Swift Interface Enhancer** - A user-centric framework for generating idiomatic Swift APIs from Kotlin Multiplatform code.

## Philosophy

Swiftify is designed with a **Swift-first mental model**:

1. **Declare what you want** - Instead of configuring transformations, you declare your desired Swift output
2. **Convention over configuration** - Sensible defaults that work out of the box
3. **Preview before compile** - See generated Swift code before building

## Quick Start

```kotlin
// build.gradle.kts
plugins {
    id("io.swiftify") version "1.0.0"
}

// That's it! Swiftify works with sensible defaults
```

## Features

### Sealed Classes → Swift Enums

```kotlin
// Kotlin
sealed class NetworkResult {
    data class Success(val data: String) : NetworkResult()
    data class Failure(val error: Throwable) : NetworkResult()
    object Loading : NetworkResult()
}
```

Generates:

```swift
// Swift
@frozen
public enum NetworkResult: Hashable {
    case success(data: String)
    case failure(error: Error)
    case loading
}
```

### Suspend Functions → Async/Await

```kotlin
// Kotlin
suspend fun fetchUser(id: Int): User
```

Generates:

```swift
// Swift
public func fetchUser(id: Int) async throws -> User
```

### Flow → AsyncSequence

```kotlin
// Kotlin
fun observeUpdates(): Flow<Update>
```

Generates Swift AsyncSequence support.

### Default Arguments

```kotlin
// Kotlin
fun search(query: String, limit: Int = 10, offset: Int = 0): List<Result>
```

Generates Swift overloads with default values.

## Configuration

### DSL Configuration (Optional)

```kotlin
swiftify {
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

### Annotation-Based

```kotlin
@SwiftEnum(name = "AppResult", exhaustive = true)
sealed class Result { ... }

@SwiftAsync
suspend fun fetchData(): Data
```

## Preview

```bash
./gradlew swiftifyPreview
./gradlew swiftifyPreview --class=com.example.NetworkResult
```

## Module Structure

```
swiftify/
├── swiftify-annotations/       # @SwiftEnum, @SwiftAsync, etc.
├── swiftify-common/            # Core types (SwiftType, SwiftEnumSpec, etc.)
├── swiftify-dsl/               # User-facing DSL
├── swiftify-analyzer/          # KSP-based Kotlin analyzer
├── swiftify-generator/         # Swift code generator
├── swiftify-linker/            # Kotlin compiler linker plugin
├── swiftify-gradle-plugin/     # Gradle plugin
├── swiftify-runtime/           # Runtime support
└── swiftify-tests/             # Test suites
```

## Development

Built with TDD (Test-Driven Development):

```bash
# Run tests
./gradlew test

# Build
./gradlew build

# Preview Swift output
./gradlew swiftifyPreview
```

## Test Coverage

- 58 unit tests covering:
  - Swift type representations
  - Enum spec and generation
  - Async function spec and generation
  - DSL configuration

## License

Apache 2.0
