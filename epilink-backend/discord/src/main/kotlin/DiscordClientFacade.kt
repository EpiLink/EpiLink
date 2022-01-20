/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.backend.discord

import org.epilink.backend.config.DiscordEmbed

/**
 * This interface should be implemented by facades that abstract away an actual Discord client for the EpiLink Discord
 * bot.
 *
 * All the functions are suspending and, no matter what the original client uses as a method for providing asynchronous
 * operations, the operations of this interface *must not be blocking*, only suspending. They should suspend until the
 * operation has been successfully processed by Discord.
 *
 * Because the bot may have joined guilds it does not have configurations for, the bot has a concept of monitored
 * guilds.
 *
 * - Guilds in which the bot is connected and has configurations for are said to be **monitored**.
 * - Guilds in which the bot is connected but does *not* have configurations for are said to be **unmonitored**.
 * - Guilds in which the bot is not but has configurations for are said to be **orphaned**. Not currently checked.
 */
@Suppress("TooManyFunctions")
interface DiscordClientFacade {

    /**
     * Start this client. This function should suspend until the bot is ready to process all other functions.
     */
    suspend fun start()

    /**
     * Send a direct message to a user.
     *
     * @param discordId The Discord user who should receive the message
     * @param embed The embed to send to the user
     */
    suspend fun sendDirectMessage(discordId: String, embed: DiscordEmbed)

    /**
     * Send a message to a channel
     *
     * @param channelId The ID of the channel where the message should be sent
     * @param embed The embed to send to the channel
     * @return The ID of the sent message
     */
    suspend fun sendChannelMessage(channelId: String, embed: DiscordEmbed): String

    /**
     * Get the guilds the bot is connected to, no matter whether they are monitored or not.
     *
     * @return A list of the IDs of ALL the guilds the bot is connected to.
     */
    suspend fun getGuilds(): List<String>

    /**
     * Checks whether a user is in a given guild or not
     *
     * @param userId The ID of the user to check
     * @param guildId The ID of the guild in which to check the presence of the user
     * @return True if the user with the given ID is present in the guild with the given ID, false otherwise
     */
    suspend fun isUserInGuild(userId: String, guildId: String): Boolean

    /**
     * Get the name of a guild with the given ID
     *
     * @param guildId The guild of which to get the name
     * @return The name of the guild with the given ID
     */
    suspend fun getGuildName(guildId: String): String

    /**
     * General-purpose role management function. Upon being called, the facade should:
     *
     * - Get the membership of the user with the given Discord ID and guild ID
     * - Add all of the given roles in the [toAdd] parameter. The member may already have some of these roles.
     * - Remove all of the given roles in the [toRemove] parameter. The member may already have some of these roles.
     *
     * The last two steps should be done in parallel and, if possible, each role addition/removal should be done in
     * parallel as well. The function should suspend until all of the operations are done.
     *
     * Given that the roles to add and remove may require no change, implementations may skip roles which are already
     * present/absent.
     *
     * @param discordId The ID of the Discord user whose roles should be modified
     * @param guildId The guild in which to modify the roles of the given user
     * @param toAdd Collection of role IDs that should be added to the user. May contain roles the user already has.
     * @param toRemove Collection of role IDs that should be removed from the user. May contain roles the user does not
     * have.
     */
    suspend fun manageRoles(discordId: String, guildId: String, toAdd: Set<String>, toRemove: Set<String>)

    /**
     * Get a [DiscordUserInfo] object for the given user ID
     *
     * @param discordId The user of whom information should be gathered
     * @return A [DiscordUserInfo] object giving information on the user
     */
    suspend fun getDiscordUserInfo(discordId: String): DiscordUserInfo

    /**
     * Retrieve a role's ID from a specific guild by searching with the role's name.
     */
    suspend fun getRoleIdByName(roleName: String, guildId: String): String?

    /**
     * Retrieve a list of members that have the given role.
     */
    suspend fun getMembersWithRole(roleId: String, guildId: String): List<String>

    /**
     * Retrieve a list of all of the members in a guild.
     */
    suspend fun getMembers(guildId: String): List<String>

    /**
     * Adds a reaction to the given message
     */
    suspend fun addReaction(channelId: String, messageId: String, reactionUnicode: String)
}

/**
 * Implementation-agnostic class for storing user information
 */
data class DiscordUserInfo(
    /**
     * The Discord ID of the user
     */
    val id: String,
    /**
     * The user's username (without the discriminator)
     */
    val username: String,
    /**
     * The user's discriminator (without the `#`)
     */
    val discriminator: String
)
