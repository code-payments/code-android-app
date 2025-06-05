package com.flipcash.app.ksp

import com.flipcash.app.ksp.annotations.FeatureFlagMarker
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.getAllSuperTypes

class FeatureFlagProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(FeatureFlagMarker::class.qualifiedName!!)
        val featureFlags = symbols.filterIsInstance<KSClassDeclaration>()
            .filter { it.getAllSuperTypes().any { superType -> superType.declaration.qualifiedName?.asString() == "com.flipcash.app.featureflags.FeatureFlag" } }
            .map { it.qualifiedName!!.asString() }
            .toList()

        if (featureFlags.isNotEmpty()) {
            val fileContent = """
                package com.flipcash.app.featureflags
                
                import com.flipcash.app.featureflags.FeatureFlag.*
                
                object FeatureFlagEntries {
                    val entries: List<FeatureFlag> = listOf(
                        ${featureFlags.joinToString(",\n                        ") { it.substringAfterLast(".") }}
                    )
                }
            """.trimIndent()

            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray()),
                packageName = "com.flipcash.app.featureflags",
                fileName = "FeatureFlagEntries"
            )
            file.write(fileContent.toByteArray())
            file.close()
        }

        return emptyList()
    }
}

class FeatureFlagProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return FeatureFlagProcessor(environment.codeGenerator, environment.logger)
    }
}