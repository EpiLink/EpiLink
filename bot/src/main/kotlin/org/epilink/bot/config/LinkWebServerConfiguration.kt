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
    val enableAdminEndpoints: Boolean = true
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
    return reports
}
