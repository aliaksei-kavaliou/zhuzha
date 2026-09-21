package com.inkfirst.app.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * Friendly label for exceptions thrown by the Gemini call path. Collapses
 * the family of "your device can't reach the host" exceptions into a single
 * plain-language line; otherwise falls back to the exception's message, then
 * its class name so nothing surfaces as bare "Unknown error" without a
 * diagnostic hint. Callers use this in place of `e.message ?: "…"`.
 *
 * It used to say "No network. Check WiFi." for that whole family, which was
 * confidently wrong on 2026-09-21: a first-request DNS miss produced it while
 * the WiFi was validated and the API host answered a ping in 19ms, and the
 * reader went off to inspect a connection that was never the problem. The
 * client now retries a failed CONNECTION twice before anything reaches here
 * (see [com.inkfirst.core.KtorGeminiClient]), so this line means three
 * attempts failed — worth blaming the connection for, but not worth naming a
 * cause the app cannot actually see.
 */
fun geminiErrorLabel(e: Throwable): String {
    if (isTimeout(e)) return "Gemini took too long to answer. Try again."
    if (isNoNetworkFailure(e)) return "Couldn't reach Gemini. Check the connection and try again."
    e.message?.takeIf { it.isNotBlank() }?.let { return it }
    return e::class.simpleName?.let { "$it (no message)" } ?: "Unknown error"
}

/**
 * A timeout is not the same failure as an unreachable host and must not
 * borrow its wording: the request DID leave, and telling someone to check a
 * connection that plainly worked sends them to debug the wrong thing.
 */
private fun isTimeout(e: Throwable): Boolean {
    var current: Throwable? = e
    while (current != null) {
        if (current is SocketTimeoutException) return true
        current = current.cause
    }
    return false
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
            -> return true
        }
        current = current.cause
    }
    return false
}
