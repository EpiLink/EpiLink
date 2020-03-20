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
annotation class ApiEndpoint(val value: String)
