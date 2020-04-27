/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.rulebook

import java.time.Duration

/**
 * Equivalent to `Duration.ofDays(value)`.
 */
val Int.days: Duration
    get() = Duration.ofDays(this.toLong())

/**
 * Equivalent to `Duration.ofHours(value)`.
 */
val Int.hours: Duration
    get() = Duration.ofHours(this.toLong())

/**
 * Equivalent to `Duration.ofMinutes(value)`.
 */
val Int.minutes: Duration
    get() = Duration.ofMinutes(this.toLong())

/**
 * Equivalent to `Duration.ofSeconds(value)`.
 */
val Int.seconds: Duration
    get() = Duration.ofSeconds(this.toLong())
