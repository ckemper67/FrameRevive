package com.framerevive.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    @Test fun stepsDoubleAndStopAtTheCap() {
        assertEquals(2_000L, Backoff.stepMs(0))
        assertEquals(4_000L, Backoff.stepMs(1))
        assertEquals(8_000L, Backoff.stepMs(2))
        assertEquals(Backoff.MAX_MS, Backoff.stepMs(Backoff.MAX_ATTEMPT))
        assertEquals("a huge attempt count must not overflow", Backoff.MAX_MS, Backoff.stepMs(64))
    }

    @Test fun jitterSpansTheWholeStep() {
        val random = Random(11)
        for (attempt in 0..6) {
            val step = Backoff.stepMs(attempt)
            val delays = (1..500).map { Backoff.delayMs(attempt, random) }
            assertTrue("every delay must sit inside [0, $step]", delays.all { it in 0..step })
            assertTrue("the low part of the step must be reachable", delays.min() < step / 4)
            assertTrue("the high part of the step must be reachable", delays.max() > step / 2)
        }
    }

    @Test fun jitterActuallyVaries() {
        val random = Random(5)
        val delays = (1..50).map { Backoff.delayMs(3, random) }.toSet()
        assertTrue("retries must not align on one value: $delays", delays.size > 10)
    }

    @Test fun onlyTransportFailuresAreWorthRetrying() {
        assertTrue(Backoff.retryable(UnknownHostException("photos.google.com")))
        assertTrue(Backoff.retryable(SocketTimeoutException("read timed out")))
        assertTrue(Backoff.retryable(ConnectException("refused")))
        assertTrue(Backoff.retryable(IOException("reset by peer")))
        assertTrue("wrapped failures are unwrapped", Backoff.retryable(RuntimeException(IOException("reset"))))
        assertFalse(Backoff.retryable(IllegalArgumentException("bad version code")))
        assertFalse(Backoff.retryable(null))
        val loop = RuntimeException("self")
        assertFalse("a self-referencing cause must not recurse", Backoff.retryable(loop))
    }
}
