/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.discord

import org.epilink.bot.discord.TargetParseResult.Success.Everyone
import org.epilink.bot.discord.TargetParseResult.Success.RoleById
import org.epilink.bot.discord.TargetParseResult.Success.RoleByName
import org.epilink.bot.discord.TargetParseResult.Success.UserById
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/*
 User selector:
 - Ping someone <@userId> (special Discord mention format)
 - Ping a role <@&roleId> (special Discord mention format)
 - By user id userId
 - By role name |roleName
 - By role id /roleId
 - Everyone !everyone
 */

/**
 * Component that implements the user selector parsing feature
 */
interface DiscordTargets {
    /**
     * Parse the Discord target, without any additional processing. The result can then be resolved with
     * [resolveDiscordTarget]?
     */
    fun parseDiscordTarget(target: String): TargetParseResult

    /**
     * Resolve a parsed Discord target, making a call to the Discord API to find the ID of a role by its name.
     */
    suspend fun resolveDiscordTarget(parsedTarget: TargetParseResult.Success, guildId: String): TargetResult
}

/**
 * Possible results of a target parse
 */
sealed class TargetParseResult {
    /**
     * Common supertype for successful parsing results
     */
    sealed class Success : TargetParseResult() {
        /**
         * A parsed target for a user with an ID
         *
         * @property id The ID of the targeted user
         */
        data class UserById(val id: String) : Success()

        /**
         * A parsed target for a role with a name
         *
         * @property name The name of the targeted role
         */
        data class RoleByName(val name: String) : Success()

        /**
         * A parsed target for a role with an ID
         *
         * @property id The ID of the targeted role
         */
        data class RoleById(val id: String) : Success()

        /**
         * A parsed target for the "everyone" special value
         */
        object Everyone : Success()
    }

    /**
     * Result returned when an error occurs (meaning that it could not be parsed into anything)
     */
    object Error : TargetParseResult()
}

/**
 * The real target result
 */
sealed class TargetResult {
    /**
     * A user target (by id)
     *
     * @property id The ID of the targeted user
     */
    data class User(val id: String) : TargetResult()

    /**
     * A role target (by id)
     *
     * @property id The ID of the targeted role
     */
    data class Role(val id: String) : TargetResult()

    /**
     * A special value that means "everyone"
     */
    object Everyone : TargetResult()

    /**
     * The role name could not be resolved (e.g. the role does not exist)
     *
     * @property name The name of the role that could not be resolved into an ID
     */
    data class RoleNotFound(val name: String) : TargetResult()
}

@OptIn(KoinApiExtension::class)
internal class DiscordTargetsImpl : DiscordTargets, KoinComponent {
    private val discord: DiscordClientFacade by inject()

    private val angleBracketsPattern = Regex("""<@(?:(&)?|!?)(\d+)>""")

    override fun parseDiscordTarget(target: String): TargetParseResult {
        val angled = angleBracketsPattern.matchEntire(target)
        if (angled != null) {
            // Pinged someone or pinged a role
            val isRole = angled.groups[1] != null
            @Suppress("UnsafeCallOnNullableType") // Cannot be null on match due to Regex
            val id = angled.groups[2]!!.value
            return if (isRole) RoleById(id) else UserById(id)
        } else {
            return when (target.first()) {
                '|' -> RoleByName(target.drop(1))
                '/' -> RoleById(target.drop(1))
                '!' ->
                    if (target == "!everyone") {
                        Everyone
                    } else TargetParseResult.Error
                else ->
                    if (target.matches(Regex("\\d+"))) {
                        UserById(target)
                    } else TargetParseResult.Error
            }
        }
    }

    override suspend fun resolveDiscordTarget(parsedTarget: TargetParseResult.Success, guildId: String): TargetResult =
        when (parsedTarget) {
            is UserById ->
                TargetResult.User(parsedTarget.id)
            is RoleByName ->
                discord.getRoleIdByName(parsedTarget.name, guildId)?.let { TargetResult.Role(it) }
                    ?: TargetResult.RoleNotFound(parsedTarget.name)
            is RoleById ->
                TargetResult.Role(parsedTarget.id)
            Everyone ->
                TargetResult.Everyone
        }
}
