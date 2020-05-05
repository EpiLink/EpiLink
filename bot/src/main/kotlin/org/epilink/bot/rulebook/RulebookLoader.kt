/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        defaultImports("org.epilink.bot.rulebook.*", "io.ktor.http.*")
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
