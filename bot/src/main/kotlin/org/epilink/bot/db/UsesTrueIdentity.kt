/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.db

/**
 * Denotes that a specific part of EpiLink accesses, in one way or another, the
 * identity of users.
 *
 * Uses can be marked with `@UsesTrueIdentity` if the uses of the marked element
 * themselves must also opt-in, or with `@OptIn(UsesTrueIdentity::class)` if the
 * true identity is not leaked outside of the marked element.
 *
 * This annotation does not actually guarantee much, and is used as a way to
 * track internal usage, so that we are aware of where the true identity is
 * used.
 *
 * If opting-in, please add a comment explaining why you require access.
 */
@RequiresOptIn(
    message = "Accessors of true identities must be marked with @UsesTrueIdentity",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class UsesTrueIdentity