package io.swiftify.dsl

/**
 * DSL marker for Swiftify configuration.
 */
@DslMarker
annotation class SwiftifyDsl

/**
 * Entry point for Swiftify DSL configuration.
 *
 * Example:
 * ```kotlin
 * swiftify {
 *     sealedClasses {
 *         transformToEnum(exhaustive = true)
 *     }
 *     suspendFunctions {
 *         transformToAsync(throwing = true)
 *     }
 * }
 * ```
 */
fun swiftify(block: SwiftifySpecBuilder.() -> Unit): SwiftifySpec {
    return SwiftifySpecBuilder().apply(block).build()
}

/**
 * The complete Swiftify configuration specification.
 */
data class SwiftifySpec(
    val defaults: Defaults,
    val sealedClassRules: List<SealedClassRule>,
    val suspendFunctionRules: List<SuspendFunctionRule>,
    val flowRules: List<FlowRule>,
    val packageRules: Map<String, PackageSpec>,
    val explicitMappings: Map<String, ExplicitMapping>,
    val typeMappings: Map<String, String>
) {
    data class Defaults(
        val transformSealedClassesToEnums: Boolean = true,
        val transformSuspendToAsync: Boolean = true,
        val transformFlowToAsyncSequence: Boolean = true,
        val maxDefaultArguments: Int = 5
    )
}

data class SealedClassRule(
    val exhaustive: Boolean = true,
    val conformances: List<String> = emptyList()
)

data class SuspendFunctionRule(
    val throwing: Boolean = true
)

data class FlowRule(
    val useAsyncSequence: Boolean = true
)

data class PackageSpec(
    val sealedClassRules: List<SealedClassRule> = emptyList(),
    val suspendFunctionRules: List<SuspendFunctionRule> = emptyList()
)

data class ExplicitMapping(
    val swiftName: String,
    val exhaustive: Boolean = true,
    val conformances: List<String> = emptyList(),
    val caseRenamings: Map<String, String> = emptyMap()
)

/**
 * Builder for SwiftifySpec.
 */
@SwiftifyDsl
class SwiftifySpecBuilder {
    private var defaults = SwiftifySpec.Defaults()
    private val sealedClassRules = mutableListOf<SealedClassRule>()
    private val suspendFunctionRules = mutableListOf<SuspendFunctionRule>()
    private val flowRules = mutableListOf<FlowRule>()
    private val packageRules = mutableMapOf<String, PackageSpec>()
    private val explicitMappings = mutableMapOf<String, ExplicitMapping>()
    private val typeMappings = mutableMapOf<String, String>()

    fun defaults(block: DefaultsBuilder.() -> Unit) {
        defaults = DefaultsBuilder(defaults).apply(block).build()
    }

    fun sealedClasses(block: SealedClassRuleBuilder.() -> Unit) {
        sealedClassRules += SealedClassRuleBuilder().apply(block).build()
    }

    fun suspendFunctions(block: SuspendFunctionRuleBuilder.() -> Unit) {
        suspendFunctionRules += SuspendFunctionRuleBuilder().apply(block).build()
    }

    fun flowTypes(block: FlowRuleBuilder.() -> Unit) {
        flowRules += FlowRuleBuilder().apply(block).build()
    }

    fun inPackage(packageName: String, block: PackageSpecBuilder.() -> Unit) {
        packageRules[packageName] = PackageSpecBuilder().apply(block).build()
    }

    fun String.toSwiftEnum(block: ExplicitMappingBuilder.() -> Unit) {
        explicitMappings[this] = ExplicitMappingBuilder().apply(block).build()
    }

    fun typeMapping(block: TypeMappingBuilder.() -> Unit) {
        TypeMappingBuilder(typeMappings).apply(block)
    }

    fun build() = SwiftifySpec(
        defaults = defaults,
        sealedClassRules = sealedClassRules.toList(),
        suspendFunctionRules = suspendFunctionRules.toList(),
        flowRules = flowRules.toList(),
        packageRules = packageRules.toMap(),
        explicitMappings = explicitMappings.toMap(),
        typeMappings = typeMappings.toMap()
    )
}

@SwiftifyDsl
class DefaultsBuilder(private var current: SwiftifySpec.Defaults) {
    var transformSealedClassesToEnums: Boolean = current.transformSealedClassesToEnums
    var transformSuspendToAsync: Boolean = current.transformSuspendToAsync
    var transformFlowToAsyncSequence: Boolean = current.transformFlowToAsyncSequence
    var maxDefaultArguments: Int = current.maxDefaultArguments

    fun build() = SwiftifySpec.Defaults(
        transformSealedClassesToEnums = transformSealedClassesToEnums,
        transformSuspendToAsync = transformSuspendToAsync,
        transformFlowToAsyncSequence = transformFlowToAsyncSequence,
        maxDefaultArguments = maxDefaultArguments
    )
}

@SwiftifyDsl
class SealedClassRuleBuilder {
    private var exhaustive: Boolean = true
    private val conformances = mutableListOf<String>()

    fun transformToEnum(exhaustive: Boolean = true) {
        this.exhaustive = exhaustive
    }

    fun conformTo(vararg protocols: String) {
        conformances += protocols
    }

    fun build() = SealedClassRule(
        exhaustive = exhaustive,
        conformances = conformances.toList()
    )
}

@SwiftifyDsl
class SuspendFunctionRuleBuilder {
    private var throwing: Boolean = true

    fun transformToAsync(throwing: Boolean = true) {
        this.throwing = throwing
    }

    fun build() = SuspendFunctionRule(throwing = throwing)
}

@SwiftifyDsl
class FlowRuleBuilder {
    private var useAsyncSequence: Boolean = true

    fun transformToAsyncSequence() {
        useAsyncSequence = true
    }

    fun build() = FlowRule(useAsyncSequence = useAsyncSequence)
}

@SwiftifyDsl
class PackageSpecBuilder {
    private val sealedClassRules = mutableListOf<SealedClassRule>()
    private val suspendFunctionRules = mutableListOf<SuspendFunctionRule>()

    fun sealedClasses(block: SealedClassRuleBuilder.() -> Unit) {
        sealedClassRules += SealedClassRuleBuilder().apply(block).build()
    }

    fun suspendFunctions(block: SuspendFunctionRuleBuilder.() -> Unit) {
        suspendFunctionRules += SuspendFunctionRuleBuilder().apply(block).build()
    }

    fun build() = PackageSpec(
        sealedClassRules = sealedClassRules.toList(),
        suspendFunctionRules = suspendFunctionRules.toList()
    )
}

@SwiftifyDsl
class ExplicitMappingBuilder {
    var name: String = ""
    var exhaustive: Boolean = true
    private val conformances = mutableListOf<String>()
    private val caseRenamings = mutableMapOf<String, String>()

    fun conformTo(vararg protocols: String) {
        conformances += protocols
    }

    fun case(kotlinName: String, swiftName: String) {
        caseRenamings[kotlinName] = swiftName
    }

    fun build() = ExplicitMapping(
        swiftName = name,
        exhaustive = exhaustive,
        conformances = conformances.toList(),
        caseRenamings = caseRenamings.toMap()
    )
}

@SwiftifyDsl
class TypeMappingBuilder(private val mappings: MutableMap<String, String>) {
    infix fun String.mapTo(swiftType: String) {
        mappings[this] = swiftType
    }
}
