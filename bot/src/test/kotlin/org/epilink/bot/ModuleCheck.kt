package org.epilink.bot

import io.mockk.mockk
import org.epilink.bot.config.rulebook.Rulebook
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import kotlin.test.AfterTest
import kotlin.test.Test

class ModuleCheck : KoinTest {
    private val cfgMock = minimalConfig
    private val rulebookMock = mockk<Rulebook>(relaxed = true)

    @Test
    fun checkEpilinkModules() {
        with(LinkServerEnvironment(cfgMock, rulebookMock)) {
            startKoin {
                modules(epilinkBaseModule, epilinkWebModule, epilinkDiscordModule)
            }.logger(PrintLogger(Level.INFO)).checkModules()
        }
    }

    @AfterTest
    fun stop() {
        stopKoin()
    }
}