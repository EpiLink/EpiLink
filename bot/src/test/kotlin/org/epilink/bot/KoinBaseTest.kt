/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.test.KoinTest
import kotlin.test.*

open class KoinBaseTest(
    private val module: Module
) : KoinTest {

    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(module)
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

}