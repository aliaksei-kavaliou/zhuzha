package com.inkfirst.app.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * Friendly label for exceptions thrown by the Gemini call path. Collapses
 * the family of "your device can't reach the internet" exceptions into a
 * single plain-language line; otherwise falls back to the exception's
 * message, then its class name so nothing surfaces as bare "Unknown error"
 * without a diagnostic hint. Callers use this in place of `e.message ?: "…"`.
 */
fun geminiErrorLabel(e: Throwable): String {
    if (isNoNetworkFailure(e)) return "No network. Check WiFi."
    e.message?.takeIf { it.isNotBlank() }?.let { return it }
    return e::class.simpleName?.let { "$it (no message)" } ?: "Unknown error"
}

/**
 * Ktor CIO wraps low-level exceptions, so we walk the cause chain rather
 * than pattern-matching only the top-level throwable.
 */
private fun isNoNetworkFailure(e: Throwable): Boolean {
    var current: Throwable? = e
    while (current != null) {
        when (current) {
            is UnresolvedAddressException,
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            -> return true
        }
        current = current.cause
    }
    return false
}
