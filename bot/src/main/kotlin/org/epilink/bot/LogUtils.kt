/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
package org.epilink.bot

import org.slf4j.Logger

/**
 * Send a lazily constructed debug message only if debug logging is enabled
 */
inline fun Logger.debug(lazyMsg: () -> String) {
    if(isDebugEnabled) {
        debug(lazyMsg())
    }
}

/**
 * Send a lazily constructed debug message only if debug logging is enabled
 */
inline fun Logger.debug(ex: Throwable, lazyMsg: () -> String) {
    if(isDebugEnabled) {
        debug(lazyMsg(), ex)
    }
}

/**
 * Send a lazily constructed trace message only if trace logging is enabled
 */
inline fun Logger.trace(lazyMsg: () -> String) {
    if(isTraceEnabled) {
        trace(lazyMsg())
    }
}

/**
 * Send the given info message if debug is disabled, or the given debug message if debug is enabled
 */
inline fun Logger.infoOrDebug(infoMessage: String, lazyDebugMsg: () -> String) {
    if(isDebugEnabled) {
        debug(lazyDebugMsg())
    } else {
        info(infoMessage)
    }
}