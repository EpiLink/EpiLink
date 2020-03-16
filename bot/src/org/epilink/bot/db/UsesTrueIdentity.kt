package org.epilink.bot.db

/**
 * Denotes that a specific part of Epilink accesses, in one way or another, the
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