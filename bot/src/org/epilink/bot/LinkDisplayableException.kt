package org.epilink.bot

/**
 * Exceptions handled within EpiLink should be using the LinkException type.
 *
 * @see LinkDisplayableException
 */
open class LinkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An exception that happens within EpiLink whose message is end-user friendly.
 */
open class LinkDisplayableException(displayableMessage: String, val isEndUserAtFault: Boolean, cause: Throwable? = null) :
    LinkException(displayableMessage, cause)