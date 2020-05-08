/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import org.epilink.bot.http.LinkBackEnd
import org.epilink.bot.http.LinkBackEndImpl
import org.koin.dsl.module


class BackEndTest : KoinBaseTest(
    module {
        single<LinkBackEnd> { LinkBackEndImpl() }
    }
) {
    // TODO Add installation tests (specifically, check that the installation actually installs all expected routes

/*    private fun withTestEpiLink(block: TestApplicationEngine.() -> Unit) =
        withTestApplication({
            with(get<LinkBackEnd>()) {
                installFeatures()
            }
            routing {
                with(get<LinkBackEnd>()) { installErrorHandling() }
                // TODO Remove once no routes are in the LinkBackEnd class
                with(get<LinkBackEnd>() as LinkBackEndImpl) { epilinkApiV1() }
            }
        }, block) */
}