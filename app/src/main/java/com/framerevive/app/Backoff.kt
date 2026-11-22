package com.framerevive.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * Spacing for retries when Google cannot be reached.
 *
 * A fixed or immediate retry makes a frame that lost the network hammer the router and its own battery, and
 * two frames that fail together retry in lockstep for as long as the outage lasts. Each attempt therefore
 * waits twice as long as the last, capped, and the wait is jittered so retries scatter.
 */
object Backoff {
    const val BASE_MS = 2_000L
    const val MAX_MS = 15 * 60_000L
    const val MAX_ATTEMPT = 12

    /** The exponential step for an attempt count: 2 s, 4 s, 8 s ... capped at fifteen minutes. */
    fun stepMs(attempt: Int): Long = (BASE_MS shl attempt.coerceIn(0, MAX_ATTEMPT)).coerceAtMost(MAX_MS)

    /** Full jitter: a uniform wait anywhere in [0, step], so retries cannot align at all. */
    fun delayMs(attempt: Int, random: Random = Random.Default): Long = random.nextLong(stepMs(attempt) + 1)

    /** Network and transport failures are worth retrying; a bad argument or a parse error is not. */
    fun retryable(error: Throwable?): Boolean = when (error) {
        null -> false
        is UnknownHostException, is SocketTimeoutException, is ConnectException, is IOException -> true
        else -> if (error.cause === error) false else retryable(error.cause)
    }
}
