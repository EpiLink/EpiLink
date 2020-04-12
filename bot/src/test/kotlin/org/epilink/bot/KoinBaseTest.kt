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