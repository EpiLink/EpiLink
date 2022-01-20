/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.rulebook

import kotlin.test.*

class RulebookHelpersTest {
    private val innerMap = mapOf("hmmmm" to "yes", "x" to 'y', "E" to 53, "x" to null)
    private val innerList = listOf("no", "maybe", "ey", 59)
    private val mapTest = mapOf(
        "potatoes" to "carrots",
        "yes" to innerList,
        "nice" to "not so nice",
        "thonk" to innerMap,
        "hey" to null,
        "hello" to 99
    )
    private val listTest = listOf("a", 'b', 3, innerMap, null, "floor", innerList)

    @Test
    fun `Test Map getMap`() {
        assertSame(innerMap, mapTest.getMap("thonk"))
    }

    @Test
    fun `Test Map getMap cast error`() {
        assertFails { mapTest.getMap("yes") }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }

    @Test
    fun `Test Map getString`() {
        assertEquals("carrots", mapTest.getString("potatoes"))
    }

    @Test
    fun `Test Map getString cast error`() {
        assertFails { mapTest.getString("hello") }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }

    @Test
    fun `Test Map getList`() {
        assertSame(innerList, mapTest.getList("yes"))
    }

    @Test
    fun `Test Map getList cast error`() {
        assertFails { mapTest.getList("thonk") }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }

    @Test
    fun `Test List getMap`() {
        assertSame(innerMap, listTest.getMap(3))
    }

    @Test
    fun `Test List getMap cast error`() {
        assertFails { listTest.getMap(1) }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }

    @Test
    fun `Test List getString`() {
        assertEquals("floor", listTest.getString(5))
    }

    @Test
    fun `Test List getString cast error`() {
        assertFails { listTest.getString(2) }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }

    @Test
    fun `Test List getList`() {
        assertSame(innerList, listTest.getList(6))
    }

    @Test
    fun `Test List getList cast error`() {
        assertFails { listTest.getList(0) }.apply {
            assert(message!!.contains("Invalid format"))
        }
    }
}
