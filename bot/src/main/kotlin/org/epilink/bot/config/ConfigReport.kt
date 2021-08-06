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
 * Base class for elements that are reported while scanning the configuration
 * for error
 *
 * @property message The message that describes this report element
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
