/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot.config

/**
 * HTTP/Web server options
 *
 * @see LinkConfiguration.server
 */
data class LinkWebServerConfiguration(
    /**
     * The port to be used by the server
     */
    val port: Int,
    /**
     * Determine which (possibly de-facto) standard to follow for proxy headers support.
     */
    val proxyType: ProxyType,
    /**
     * The URL to the front-end, with a trailing slash, or null if the front-end is bundled
     */
    val frontendUrl: String?,
    /**
     * A list of footers that should be displayed in the front-end
     */
    val footers: List<LinkFooterUrl> = listOf(),
    /**
     * Logo resource asset that is passed to the front-end
     */
    val logo: ResourceAssetConfig? = ResourceAssetConfig(),
    /**
     * Background resource asset that is passed to the front-end
     */
    val background: ResourceAssetConfig? = ResourceAssetConfig(),
    /**
     * Contact information for instance maintainers
     *
     * @since 0.2.0
     */
    val contacts: List<LinkContactInformation> = listOf(),
    /**
     * True if administrative endpoints should be enabled, false if they should be disabled.
     */
    val enableAdminEndpoints: Boolean = true,
    /**
     * Number of seconds a user needs to wait after a relink (or a link-sensitive event like a ban) in order to unlink
     */
    val unlinkCooldown: Long = 3600L,
    /**
     * Rate limiting profile
     */
    val rateLimitingProfile: RateLimitingProfile = RateLimitingProfile.Harsh,
    // TODO document (and document this)
    //      also make this testable?
    val disableCorsSecurity: Boolean = false
)

/**
 * Used in [LinkWebServerConfiguration.proxyType] to set the kind of proxy expected
 */
enum class ProxyType {
    /**
     * No proxy used: do not enable header support
     */
    None,

    /**
     * Proxy used with the de-facto standard X-Forwarded-* headers
     */
    XForwarded,

    /**
     * Proxy used with the standard Forwarded header
     */
    Forwarded
}

/**
 * Represents the calls/minutes allowed under different rate limiting profiles
 */
@Suppress("unused")
enum class RateLimitingProfile(
    /**
     * Calls/minutes for the meta API. Applies to all users per IP address
     */
    val metaApi: Int,
    /**
     * Calls/minutes for the user API. Applies to individual users per IP address.
     */
    val userApi: Int,
    /**
     * Calls/minutes for the registration API. Applies to all users per IP address.
     */
    val registrationApi: Int,
    /**
     * Calls/minutes for the administration API. Applies to all users per IP address.
     */
    val adminApi: Int
) {
    /**
     * Disables rate limiting entirely
     */
    Disabled(-1, -1, -1, -1),

    /**
     * Lenient profile for high-usage periods.
     */
    Lenient(300, 30, 150, 30),

    /**
     * Standard rate limiting profile, recommended for instances which host a lot of users on the regular.
     */
    Standard(100, 20, 100, 30),

    /**
     * Harsh rate limiting profile, recommended for regular use.
     */
    Harsh(50, 10, 20, 30)
}

/**
 * A footer that should be displayed in the front-end
 */
data class LinkFooterUrl(
    /**
     * The name (title) of the link, displayed in the front-end
     */
    val name: String,
    /**
     * The actual URL
     */
    val url: String
)

/**
 * Represents the contact information for a single person
 *
 * @since 0.2.0
 */
data class LinkContactInformation(
    /**
     * The name of the person
     */
    val name: String,
    /**
     * Their email address
     */
    val email: String
)

/**
 * Check the web server's configuration
 */
fun LinkWebServerConfiguration.check(): List<ConfigReportElement> {
    val reports = mutableListOf<ConfigReportElement>()
    if (this.frontendUrl?.endsWith("/") == false) { // Equality check because left side can be null
        reports += ConfigError(
            true,
            "The frontendUrl value in the server config must have a trailing slash (add a / at the end of your URL)"
        )
    }
    reports += ConfigInfo("Admin endpoints (/api/v1/admin/...) are ${if (enableAdminEndpoints) "enabled" else "disabled"}")
    when(rateLimitingProfile) {
        RateLimitingProfile.Lenient ->
            reports += ConfigWarning("Rate limiting profile set to Lenient, which may not be strict enough to protect EpiLink from DoS attacks.")
        RateLimitingProfile.Disabled ->
            reports += ConfigError(false, "Rate limiting profile set to Disabled. Spam protection is disabled, this leaves your server open for abuse!")
        else -> { /* nothing to report */ }
    }
    return reports
}
