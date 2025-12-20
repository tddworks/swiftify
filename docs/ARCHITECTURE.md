# Swiftify Architecture

## Overview

Swiftify is a Gradle plugin that generates Swift code and embeds it into Kotlin/Native frameworks during the build process.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Build Pipeline                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐  │
│  │  Kotlin  │───▶│ Analyzer │───▶│Generator │───▶│  Linker  │  │
│  │  Source  │    │          │    │          │    │          │  │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘  │
│       │               │               │               │         │
│       ▼               ▼               ▼               ▼         │
│  @SwiftDefaults   KotlinDecl    Swift Code     Framework        │
│  @SwiftFlow       AST Nodes     Extensions     + Swift          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Modules

### swiftify-annotations

Runtime annotations used in Kotlin source code.

```
io.swiftify.annotations
├── @SwiftDefaults    # Generate convenience overloads
├── @SwiftFlow        # Wrap Flow as AsyncStream
├── @SwiftEnum        # Transform sealed class to enum
├── @SwiftCase        # Customize enum case
├── @SwiftName        # Custom Swift name
├── @SwiftParameter   # Customize parameter
└── @SwiftifyIgnore   # Exclude from processing
```

### swiftify-analyzer

Analyzes Kotlin source code to extract declarations marked with Swiftify annotations.

```kotlin
class KotlinDeclarationAnalyzer {
    fun analyze(source: String): List<KotlinDeclaration>
}

sealed class KotlinDeclaration {
    data class FunctionDeclaration(...)      // Functions with @SwiftDefaults
    data class FlowFunctionDeclaration(...)  // Flow functions with @SwiftFlow
    data class SealedClassDeclaration(...)   // Sealed classes with @SwiftEnum
}
```

**Key Features:**
- Regex-based source analysis (fast, no compilation needed)
- Processes functions with `@SwiftDefaults` annotation
- Extracts parameter defaults, types, and annotations

### swiftify-generator

Generates Swift source code from analyzed Kotlin declarations.

```kotlin
class SwiftDefaultsGenerator {
    fun generate(spec: SwiftDefaultsSpec): String
    fun generateConvenienceOverloads(spec: SwiftDefaultsSpec): String
}

class SwiftAsyncStreamGenerator {
    fun generate(spec: SwiftAsyncStreamSpec): String
    fun generateWithImplementation(spec: SwiftAsyncStreamSpec): String
}

class SwiftifyTransformer {
    fun transform(declarations: List<KotlinDeclaration>): TransformResult
}
```

**Generated Code Types:**
1. **Convenience Overloads** - For `@SwiftDefaults` functions
2. **AsyncStream Wrappers** - For `@SwiftFlow` functions/properties
3. **Runtime Helpers** - `SwiftifyFlowCollector` bridge class

### swiftify-dsl

User-facing Kotlin DSL for configuration.

```kotlin
swiftify {
    defaults {
        generateDefaultOverloads = true
        transformFlowToAsyncStream = true
        maxDefaultOverloads = 5
    }
    defaultParameters {
        generateOverloads(maxOverloads = 5)
    }
    flowTypes {
        transformToAsyncStream()
    }
}
```

### swiftify-linker

Embeds generated Swift code into Kotlin/Native frameworks.

```kotlin
class FrameworkProcessor {
    fun embedSwiftCode(
        frameworkPath: Path,
        swiftFiles: List<Path>,
        moduleName: String
    )
}
```

**Process:**
1. Compile Swift files with `-import-underlying-module`
2. Extract object files from compiled Swift
3. Merge object files into framework binary using `ld`
4. Install Swift module files (.swiftmodule, .swiftinterface)

### swiftify-gradle-plugin

Gradle plugin that orchestrates the build process.

```kotlin
class SwiftifyPlugin : Plugin<Project> {
    // Registers tasks and hooks into KMP build
}

class SwiftifyEmbedSwiftTask : DefaultTask() {
    // Generates and embeds Swift code
}
```

**Task Graph:**
```
compileKotlinMacosArm64
         │
         ▼
linkReleaseFrameworkMacosArm64
         │
         ▼
swiftifyEmbedSwift (runs after link)
         │
         ▼
Framework with embedded Swift
```

### swiftify-swift

Swift type specifications - the intermediate representation for Swift code generation.

```kotlin
// Package: io.swiftify.swift

data class SwiftDefaultsSpec(
    val name: String,
    val parameters: List<SwiftParameter>,
    val returnType: SwiftType,
    val isThrowing: Boolean,
    ...
)

data class SwiftAsyncStreamSpec(
    val name: String,
    val parameters: List<SwiftParameter>,
    val elementType: SwiftType,
    ...
)

sealed class SwiftType {
    data class Named(val name: String) : SwiftType()
    data class Optional(val wrapped: SwiftType) : SwiftType()
    data class Array(val elementType: SwiftType) : SwiftType()
    // ... more Swift type representations
}
```

---

## Build Flow

### 1. Configuration Phase

```
build.gradle.kts
      │
      ▼
SwiftifyPlugin.apply()
      │
      ├── Register swiftify { } extension
      ├── Auto-detect framework name
      └── Hook into link tasks
```

### 2. Analysis Phase

```
Source Files (.kt)
      │
      ▼
KotlinDeclarationAnalyzer
      │
      ├── Find @SwiftDefaults functions
      ├── Find @SwiftFlow functions/properties
      └── Extract parameters, defaults, types
      │
      ▼
List<KotlinDeclaration>
```

### 3. Generation Phase

```
List<KotlinDeclaration>
      │
      ▼
SwiftifyTransformer
      │
      ├── Generate convenience overloads
      ├── Generate AsyncStream wrappers
      └── Generate Swift helpers (FlowCollector)
      │
      ▼
Swiftify.swift + SwiftifyRuntime.swift (pure Swift)
```

### 4. Embedding Phase

```
Framework + Swift Files
      │
      ▼
FrameworkProcessor
      │
      ├── Compile Swift with swiftc
      ├── Extract .o files
      ├── Merge into framework binary
      └── Install Swift module files
      │
      ▼
Framework with embedded Swift
```

---

## Generated File Structure

```
build/generated/swiftify/
├── Swiftify.swift           # All generated Swift extensions
├── SwiftifyRuntime.swift    # Swift helpers (FlowCollector for AsyncStream)
└── SampleKit.apinotes       # Xcode API notes

build/bin/macosArm64/releaseFramework/
└── SampleKit.framework/
    ├── SampleKit                    # Binary (with embedded Swift)
    ├── Headers/
    │   └── SampleKit.h
    └── Modules/
        ├── module.modulemap
        └── SampleKit.swiftmodule/
            ├── arm64-apple-macos.swiftmodule
            ├── arm64-apple-macos.swiftinterface
            └── arm64-apple-macos.private.swiftinterface
```

---

## Key Design Decisions

### 1. Source-based Analysis

We analyze Kotlin source files directly using regex patterns instead of using KSP or compiler plugins. This approach:
- Works during framework build (no separate annotation processing)
- Fast and lightweight
- No build order dependencies

### 2. Swift Overlay Pattern

Generated Swift code uses the `-import-underlying-module` flag to extend Kotlin classes:

```swift
import SampleKit  // Import the Kotlin module

extension NotesRepository {
    // Add Swift convenience methods
    public func getNotes() async throws -> [Note] {
        return try await getNotes(limit: 10, includeArchived: false)
    }
}
```

### 3. Post-Link Embedding

Swift code is compiled and embedded after Kotlin/Native links the framework. This ensures:
- Kotlin types are available to Swift compiler
- No circular dependencies
- Clean separation of concerns

### 4. Convenience Overloads (Not Wrappers)

For suspend functions, we generate **convenience overloads** that call the Kotlin-generated async methods, rather than wrapping them:

```swift
// Generated - calls through to Kotlin's async method
func getNotes() async throws -> [Note] {
    return try await getNotes(limit: 10, includeArchived: false)
}
```

This is because Kotlin 2.0+ already generates proper `async throws` methods.

---

## Testing Strategy

```
swiftify-tests/
├── unit/           # Unit tests for generators, analyzers
├── integration/    # Integration tests for linker
└── acceptance/     # End-to-end tests with real frameworks
```

**Test Coverage:**
- Analyzer: Pattern matching, edge cases
- Generator: Code output correctness
- Linker: Binary manipulation
- Plugin: Gradle task execution
- E2E: Full build and Swift compilation
