/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import io.mockk.mockk
import org.epilink.bot.rulebook.Rulebook
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
    private val legalTextsMock = mockk<LinkLegalTexts>()

    @Test
    fun checkEpilinkModules() {
        with(
            LinkServerEnvironment(cfgMock, legalTextsMock, rulebookMock)
        ) {
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