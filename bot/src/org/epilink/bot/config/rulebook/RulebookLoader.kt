package org.epilink.bot.config.rulebook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

suspend fun loadRules(path: Path): Rulebook =
    loadRules(path.toFile().toScriptSource())

suspend fun loadRules(string: String): Rulebook =
    loadRules(string.toScriptSource())

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

suspend fun loadRules(block: RulebookBuilder.() -> Unit): Rulebook = withContext(Dispatchers.Default) {
    RulebookBuilder().apply(block).buildRulebook()
}