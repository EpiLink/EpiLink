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
import org.slf4j.Logger
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
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
 * Result of the [shouldUseCache] function.
 */
sealed class CacheAdvisory {
    /**
     * Do not use the cache at all and do not write to it.
     */
    object DoNotCache : CacheAdvisory()

    /**
     * Read the cached file.
     *
     * @property cachePath The cache path to read from
     */
    class ReadCache(val cachePath: Path) : CacheAdvisory()

    /**
     * Write to the cached file. The file denoted by the [cachePath] may not exist.
     *
     * @property cachePath The cache path to write to
     */
    class WriteCache(val cachePath: Path) : CacheAdvisory()
}

/**
 * Determine what should be done with the cache.
 */
@Suppress("BlockingMethodInNonBlockingContext") // withContext(Dispatchers.IO) so we don't care
suspend fun shouldUseCache(originalFile: Path): CacheAdvisory = withContext(Dispatchers.IO) {
    val cachedFile = originalFile.resolveSibling("${originalFile.fileName}__cached")
    when {
        Files.isRegularFile(cachedFile) -> {
            val cachedAttributes = Files.readAttributes(cachedFile, BasicFileAttributes::class.java)
            val originalAttributes = Files.readAttributes(originalFile, BasicFileAttributes::class.java)
            if (cachedAttributes.lastModifiedTime() < originalAttributes.lastAccessTime()) {
                CacheAdvisory.WriteCache(cachedFile)
            } else {
                CacheAdvisory.ReadCache(cachedFile)
            }
        }
        Files.notExists(cachedFile) -> {
            CacheAdvisory.WriteCache(cachedFile)
        }
        else -> {
            CacheAdvisory.DoNotCache
        }
    }
}

@Suppress("BlockingMethodInNonBlockingContext") // withContext(Dispatchers.IO) so we don't care
internal suspend fun CompiledScript<*>.writeScriptTo(path: Path): Unit = withContext(Dispatchers.IO) {
    ObjectOutputStream(Files.newOutputStream(path)).use { out ->
        out.writeObject(this@writeScriptTo)
    }
}

@Suppress("BlockingMethodInNonBlockingContext") // withContext(Dispatchers.IO) so we don't care
internal suspend fun readScriptFrom(path: Path): CompiledScript<*> = withContext(Dispatchers.IO) {
    ObjectInputStream(Files.newInputStream(path)).use { ins ->
        ins.readObject() as KJvmCompiledScript<*>
    }
}

internal suspend fun compileRules(source: SourceCode): CompiledScript<*> = withContext(Dispatchers.Default) {
    val compileConfig = createJvmCompilationConfigurationFromTemplate<RulebookScript> {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        implicitReceivers(RulebookBuilder::class)
        defaultImports("org.epilink.bot.rulebook.*", "io.ktor.http.*")
    }

    BasicJvmScriptingHost().compiler(source, compileConfig).valueOr {
        it.handleFailure()
    }
}

internal suspend fun evaluateRules(from: CompiledScript<*>): Rulebook {
    val builder = RulebookBuilder()
    val evalConfig = createJvmEvaluationConfigurationFromTemplate<RulebookScript> {
        implicitReceivers(builder)
    }
    BasicJvmScriptingHost().evaluator(from, evalConfig).onFailure {
        it.handleFailure()
    }
    return builder.buildRulebook()
}

private fun ResultWithDiagnostics<*>.handleFailure(): Nothing {
    reports.forEach { report ->
        println("--> ${report.severity.name}: ${report.message}")
        report.location?.start?.let { start ->
            println("    start: line ${start.line} col ${start.col}")
        }
        report.exception?.printStackTrace()
    }
    error("Failed to load rulebook")
}

/**
 * Execute the Rulebook script from the given source code, and return the rulebook that was created from it.
 *
 * If checkCache is set to true, a cache version may be loaded or written to if possible.
 */
suspend fun loadRules(source: SourceCode): Rulebook = withContext(Dispatchers.Default) {
    val script = compileRules(source)
    evaluateRules(script)
}

/**
 * Execute the rulebook script from the given source code or a cached rulebook script if such a cache is applicable and
 * the original script predates the cached one.
 */
suspend fun loadRulesWithCache(path: Path, logger: Logger?): Rulebook = withContext(Dispatchers.IO) {
    when (val adv = shouldUseCache(path)) {
        is CacheAdvisory.DoNotCache -> {
            logger?.warn("Caching was disabled automatically to avoid potential issues. Try deleting all files next to the rulebook file that have a file name ending in '__cached' ")
            loadRules(path.readScriptSource())
        }
        is CacheAdvisory.WriteCache -> {
            val compiled = compileRules(path.readScriptSource())
            logger?.info("Writing cache to ${adv.cachePath}. Next startup will be faster. You can disable caching by setting 'enableCache: false' in the configuration file.")
            runCatching { compiled.writeScriptTo(adv.cachePath) }.onFailure {
                logger?.error("Failed to write the rulebook cache to ${adv.cachePath}", it)
            }
            evaluateRules(compiled)
        }
        is CacheAdvisory.ReadCache -> {
            logger?.info("Reading a pre-compiled cache from ${adv.cachePath}. You can disable caching by setting 'enableCache: false' in the configuration file.")
            val compiled = runCatching {
                readScriptFrom(adv.cachePath)
            }.getOrElse {
                logger?.error("Failed to read cache, using the original rulebook file instead. Try deleting the cached file (${adv.cachePath}).", it)
                compileRules(path.readScriptSource())
            }
            evaluateRules(compiled)
        }
    }
}

fun Path.readScriptSource(): SourceCode =
    Files.readString(this).toScriptSource(fileName.toString().replace('.', '_'))
