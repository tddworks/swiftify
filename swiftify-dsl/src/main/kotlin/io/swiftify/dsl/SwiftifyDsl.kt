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
 *     defaultParameters {
 *         generateOverloads(maxOverloads = 5)
 *     }
 *     flowTypes {
 *         transformToAsyncStream()
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
    val defaultParameterRules: List<DefaultParameterRule>,
    val flowRules: List<FlowRule>,
    val packageRules: Map<String, PackageSpec>,
    val explicitMappings: Map<String, ExplicitMapping>,
    val typeMappings: Map<String, String>
) {
    data class Defaults(
        val transformSealedClassesToEnums: Boolean = true,
        val generateDefaultOverloads: Boolean = true,
        val transformFlowToAsyncStream: Boolean = true,
        val maxDefaultOverloads: Int = 5,
        /**
         * If true, only process functions with explicit annotations (@SwiftDefaults, @SwiftFlow).
         * If false, process all suspend functions and Flow returns based on DSL rules.
         */
        val requireAnnotations: Boolean = true
    )
}

data class SealedClassRule(
    val exhaustive: Boolean = true,
    val conformances: List<String> = emptyList()
)

/**
 * Rule for generating convenience overloads for functions with default parameters.
 */
data class DefaultParameterRule(
    val maxOverloads: Int = 5
)

data class FlowRule(
    val useAsyncStream: Boolean = true
)

data class PackageSpec(
    val sealedClassRules: List<SealedClassRule> = emptyList(),
    val defaultParameterRules: List<DefaultParameterRule> = emptyList()
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
    private val defaultParameterRules = mutableListOf<DefaultParameterRule>()
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

    /**
     * Configure how convenience overloads are generated for functions with default parameters.
     */
    fun defaultParameters(block: DefaultParameterRuleBuilder.() -> Unit) {
        defaultParameterRules += DefaultParameterRuleBuilder().apply(block).build()
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
        defaultParameterRules = defaultParameterRules.toList(),
        flowRules = flowRules.toList(),
        packageRules = packageRules.toMap(),
        explicitMappings = explicitMappings.toMap(),
        typeMappings = typeMappings.toMap()
    )
}

@SwiftifyDsl
class DefaultsBuilder(private var current: SwiftifySpec.Defaults) {
    var transformSealedClassesToEnums: Boolean = current.transformSealedClassesToEnums
    var generateDefaultOverloads: Boolean = current.generateDefaultOverloads
    var transformFlowToAsyncStream: Boolean = current.transformFlowToAsyncStream
    var maxDefaultOverloads: Int = current.maxDefaultOverloads
    /**
     * If true, only process functions with explicit annotations (@SwiftDefaults, @SwiftFlow).
     * If false, process all matching functions based on DSL rules.
     */
    var requireAnnotations: Boolean = current.requireAnnotations

    fun build() = SwiftifySpec.Defaults(
        transformSealedClassesToEnums = transformSealedClassesToEnums,
        generateDefaultOverloads = generateDefaultOverloads,
        transformFlowToAsyncStream = transformFlowToAsyncStream,
        maxDefaultOverloads = maxDefaultOverloads,
        requireAnnotations = requireAnnotations
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

/**
 * Builder for configuring convenience overload generation for default parameters.
 */
@SwiftifyDsl
class DefaultParameterRuleBuilder {
    private var maxOverloads: Int = 5

    /**
     * Configure generation of convenience overloads for functions with default parameters.
     * @param maxOverloads Maximum number of overloads to generate (default: 5)
     */
    fun generateOverloads(maxOverloads: Int = 5) {
        this.maxOverloads = maxOverloads
    }

    fun build() = DefaultParameterRule(maxOverloads = maxOverloads)
}

@SwiftifyDsl
class FlowRuleBuilder {
    private var useAsyncStream: Boolean = true

    /**
     * Transform Kotlin Flow to Swift AsyncStream.
     */
    fun transformToAsyncStream() {
        useAsyncStream = true
    }

    fun build() = FlowRule(useAsyncStream = useAsyncStream)
}

@SwiftifyDsl
class PackageSpecBuilder {
    private val sealedClassRules = mutableListOf<SealedClassRule>()
    private val defaultParameterRules = mutableListOf<DefaultParameterRule>()

    fun sealedClasses(block: SealedClassRuleBuilder.() -> Unit) {
        sealedClassRules += SealedClassRuleBuilder().apply(block).build()
    }

    fun defaultParameters(block: DefaultParameterRuleBuilder.() -> Unit) {
        defaultParameterRules += DefaultParameterRuleBuilder().apply(block).build()
    }

    fun build() = PackageSpec(
        sealedClassRules = sealedClassRules.toList(),
        defaultParameterRules = defaultParameterRules.toList()
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
