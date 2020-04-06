package org.epilink.bot

import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.LinkConfiguration
import org.epilink.bot.config.LinkDiscordConfig
import org.epilink.bot.config.LinkTokens
import org.epilink.bot.config.LinkWebServerConfiguration
import org.epilink.bot.config.rulebook.Rulebook
import org.junit.experimental.categories.Category
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.dsl.koinApplication
import org.koin.test.AutoCloseKoinTest
import org.koin.test.KoinTest
import org.koin.test.category.CheckModuleTest
import kotlin.test.Test
import org.koin.test.check.checkModules

@Category(CheckModuleTest::class)
class ModuleCheck : AutoCloseKoinTest() {
    val cfgMock = LinkConfiguration(
        "Test",
        server = LinkWebServerConfiguration(0, null),
        db = "",
        tokens = LinkTokens(
            discordToken = "",
            discordOAuthClientId = "",
            discordOAuthSecret = "",
            msftOAuthClientId = "",
            msftOAuthSecret = "",
            msftTenant = "error"
        ),
        discord = LinkDiscordConfig(null),
        redis = null
    )
    val rulebookMock = mockk<Rulebook>(relaxed = true)

    @Test
    fun checkEpilinkModules() {
        with(LinkServerEnvironment(cfgMock, rulebookMock)) {
            startKoin {
                modules(epilinkBaseModule, epilinkWebModule, epilinkDiscordModule)
            }.logger(PrintLogger(Level.INFO)).checkModules()
        }
    }
}