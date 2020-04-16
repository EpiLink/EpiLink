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
 * Send the given info message if debug is disabled, or the given debug message if debug is enabled
 */
inline fun Logger.infoOrDebug(infoMessage: String, lazyDebugMsg: () -> String) {
    if(isDebugEnabled) {
        debug(lazyDebugMsg())
    } else {
        info(infoMessage)
    }
}