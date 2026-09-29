package com.helios.frame.batch

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 3 watchdog test: a hung image must be cancelled and reported as a
 * failure instead of blocking the batch. Run:
 *   cmd /c gradlew.bat :core-batch:testDebugUnitTest
 */
class ImageTimeoutTest {

    @Test
    fun `hang past timeout is cancelled`() = runBlocking {
        val start = System.currentTimeMillis()
        try {
            BatchWorker.runWithTimeout(200) {
                delay(5_000) // simulated hang
            }
            fail("expected TimeoutCancellationException")
        } catch (t: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - start
            assertTrue("cancelled promptly, elapsed=$elapsed", elapsed < 5_000)
        }
    }

    @Test
    fun `fast image passes and batch continues after a timeout`() = runBlocking {
        // Image 1 hangs → recorded as failure; image 2 succeeds; batch continues.
        val results = mutableListOf<String>()
        val jobs = listOf<suspend () -> Unit>(
            {
                try {
                    BatchWorker.runWithTimeout(200) { delay(5_000) }
                    results.add("img0: ok")
                } catch (t: TimeoutCancellationException) {
                    results.add("img0: timed out after 200ms, skipped")
                }
            },
            {
                val v = BatchWorker.runWithTimeout(BatchWorker.PER_IMAGE_TIMEOUT_MS) { "decoded" }
                results.add("img1: $v")
            },
        )
        jobs.forEach { it() }
        assertEquals(2, results.size)
        assertTrue(results[0].contains("timed out"))
        assertEquals("img1: decoded", results[1])
    }
}
