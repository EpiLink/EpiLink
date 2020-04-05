package org.epilink.bot.config.rulebook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

/**
 * Execute the Rulebook script located at the given path, and return the rulebook that was created from it.
 */
suspend fun loadRules(path: Path): Rulebook =
    loadRules(path.toFile().toScriptSource())

/**
 * Execute the string as a Rulebook script, and return the rulebook that was created form it.
 */
suspend fun loadRules(string: String): Rulebook =
    loadRules(string.toScriptSource())

/**
 * Execute the Rulebook script from the given source code, and return the rulebook that was created from it.
 */
suspend fun loadRules(source: SourceCode): Rulebook = withContext(Dispatchers.Default) {
    val builder = RulebookBuilder()
    val compileConfig = createJvmCompilationConfigurationFromTemplate<RulebookScript> {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        implicitReceivers(RulebookBuilder::class)
        defaultImports("org.epilink.bot.config.rulebook.*", "io.ktor.http.*")
    }
    val evalConfig = createJvmEvaluationConfigurationFromTemplate<RulebookScript> {
        implicitReceivers(builder)
    }
    BasicJvmScriptingHost().eval(source, compileConfig, evalConfig).onFailure {
        it.reports.forEach { report ->
            println("--> ${report.severity.name}: ${report.message}")
            report.location?.start?.let { start ->
                println("    start: line ${start.line} col ${start.col}")
            }
            report.exception?.printStackTrace()
        }
        error("Failed to load rulebook")
    }
    builder.buildRulebook()
}

/**
 * Create a rulebook by using the Rulebook DSL directly.
 */
suspend fun loadRules(block: RulebookBuilder.() -> Unit): Rulebook = withContext(Dispatchers.Default) {
    RulebookBuilder().apply(block).buildRulebook()
}