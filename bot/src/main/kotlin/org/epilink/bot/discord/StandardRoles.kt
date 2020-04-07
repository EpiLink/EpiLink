package org.epilink.bot.discord

/**
 * The standard roles which are attributed by EpiLink automatically
 *
 * @property roleName The name of the role as it can be used in the configuration
 */
enum class StandardRoles(val roleName: String) {
    /**
     * A user who has his true identity stored in the database
     */
    Identified("_identified"),

    /**
     * A user who is authenticated through EpiLink
     */
    Known("_known")
}