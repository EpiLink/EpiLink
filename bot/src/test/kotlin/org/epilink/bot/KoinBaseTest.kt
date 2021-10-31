/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinApiExtension
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.test.KoinTest
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * A base for all classes that run tests that use Koin. This class (and its subclasses) automatically start and stop
 * Koin.
 *
 * Classes that do not use the [test] function can use [Unit] and `Unit::class` as the type for this class.
 */
open class KoinBaseTest<T : Any>(
    private val kclass: KClass<T>,
    private val module: Module
) : KoinTest {

    /**
     * Useful for specifying a second module that has more complicated behavior and cannot be put in the constructor
     * directly.
     *
     * @return A second module to add to Koin, or null if only the constructor's module should be considered.
     */
    open fun additionalModule(): Module? {
        return null
    }

    @BeforeTest
    fun setupKoin() {
        startKoin {
            val additional = additionalModule()
            if (additional != null) {
                modules(module, additional)
            } else {
                modules(module)
            }
        }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    /**
     * Run the given [block] in a coroutine, with a ready-to-use Koin DI environment.
     */
    @OptIn(KoinApiExtension::class)
    fun <R> test(block: suspend T.() -> R): R =
        runBlocking { block(getKoin().get(kclass)) }
}
