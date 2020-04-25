/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.http

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
@Repeatable
/**
 * Denotes that the annotated element is an exposed API endpoint.
 *
 * (This is only for documentation purposes)
 */
annotation class ApiEndpoint(
    /**
     * The endpoint (e.g. PUT /api/v9/example)
     */
    val value: String
)
