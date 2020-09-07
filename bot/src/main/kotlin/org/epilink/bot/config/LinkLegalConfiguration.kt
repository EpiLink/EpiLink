package org.epilink.bot.config

/**
 * The legal configuration
 */
data class LinkLegalConfiguration(
    /**
     * Terms of services, directly as a string
     */
    val tos: String? = null,
    /**
     * Terms of services as a path relative to the configuration file's location
     */
    val tosFile: String? = null,
    /**
     * Privacy policy, directly as a string
     */
    val policy: String? = null,
    /**
     * Privacy policy, as a path relative to the configuration file's location
     */
    val policyFile: String? = null,
    /**
     * The text shown below the "Remember who I am" checkbox in the front-end
     */
    val identityPromptText: String? = null
)

/**
 * Check the coherence of the legal configuration
 */
fun LinkLegalConfiguration.check(): List<ConfigReportElement> {
    val rep = mutableListOf<ConfigReportElement>()
    if (this.tos == null && this.tosFile == null) {
        rep += ConfigWarning("No ToS provided. Please provide one using in the 'legal' part of the configuration, either as a file (tosFile) or directly as a string (tos)")
    } else if (this.tos != null && this.tosFile != null) {
        rep += ConfigError(
            true,
            "Your configuration file defines both a tos and a tosFile. Please only specify one of them, either only a file (tosFile) or a string (tos)"
        )
    }

    if (this.policy == null && this.policyFile == null) {
        rep += ConfigWarning("No privacy policy provided. Please provide one using the 'legal' part of the configuration, either as a file (policyFile) or directly as a string (policy)")
    } else if (this.policy != null && this.policyFile != null) {
        rep += ConfigError(
            true,
            "Your configuration file defines both a policy and a policyFile. Please only specify one of them, either only a file (policyFile) or a string (policy)"
        )
    }

    if (this.identityPromptText == null) {
        rep += ConfigWarning("No identity prompt text provided. Please provide one using the 'legal' part of the configuration (identityPromptText)")
    }

    return rep
}