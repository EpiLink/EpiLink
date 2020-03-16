package org.epilink.bot.config

/**
 * Base class for elements that are reported while scanning the configuration
 * for error
 */
sealed class ConfigReportElement(val message: String)

/**
 * Information that can be safely ignored after the report. It is just there as
 * a non-important heads-up.
 */
class ConfigInfo(message: String) : ConfigReportElement(message)

/**
 * A warning indicates that there is significant danger involved in continuing
 * execution, although the user may already be aware of it.
 */
class ConfigWarning(message: String) : ConfigReportElement(message)

/**
 * An error means that something went wrong.
 *
 * @property shouldFail Whether the configuration is actually unusable, in
 * which case the program will exit abnormally after execution is finished.
 */
class ConfigError(val shouldFail: Boolean, message: String) :
    ConfigReportElement(message)