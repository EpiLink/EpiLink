package org.epilink.bot

import guru.zoroark.tegral.di.dsl.tegralDiModule
import guru.zoroark.tegral.di.test.check.complete
import guru.zoroark.tegral.di.test.check.modules
import guru.zoroark.tegral.di.test.check.noCycle
import guru.zoroark.tegral.di.test.check.noUnused
import guru.zoroark.tegral.di.test.check.safeInjection
import guru.zoroark.tegral.di.test.check.tegralDiCheck
import io.mockk.every
import io.mockk.mockk
import org.epilink.bot.config.Configuration
import org.epilink.bot.config.DiscordConfiguration
import org.epilink.bot.config.IdentityProviderConfiguration
import org.epilink.bot.config.ProxyType
import org.epilink.bot.config.TokensConfiguration
import org.epilink.bot.config.WebServerConfiguration
import org.epilink.bot.http.IdentityProviderMetadata
import org.epilink.bot.rulebook.Rulebook
import kotlin.test.Test

class DiCheck {
    @Test
    fun `DI Checks`() {
        val serverEnv = mockk<ServerEnvironment>()
        val configuration = Configuration(
            "Test",
            server = WebServerConfiguration(port = 123, proxyType = ProxyType.None, frontendUrl = null),
            db = "test",
            discord = DiscordConfiguration(welcomeUrl = null),
            tokens = TokensConfiguration("", "", "", "", ""),
            idProvider = IdentityProviderConfiguration("", "", null),
            redis = null
        )
        val legalTexts = LegalTexts(LegalText.Html(""), LegalText.Html(""), "")
        val idpMetadata = IdentityProviderMetadata("", "", "", "")
        val assets = Assets(ResourceAsset.None, ResourceAsset.None, ResourceAsset.None)
        tegralDiCheck {
            modules(
                createBaseModule(serverEnv, configuration),
                createWebModule(configuration, idpMetadata, legalTexts, assets),
                createDiscordModule(Rulebook(emptyMap(), null), configuration, emptyMap(), ""),
            )

            safeInjection()
            complete()
        }
    }
}
