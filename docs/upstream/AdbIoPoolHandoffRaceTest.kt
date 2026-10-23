package maestro.android

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reproduces a spurious "all 16 adb I/O workers are busy" rejection from [newAdbIoExecutor].
 *
 * The pool hands calls over a SynchronousQueue with a hard cap of [ADB_IO_POOL_MAX] threads. At the
 * cap, a call is accepted only if a worker is blocked in poll() at that exact instant; otherwise it is
 * rejected, even when a worker is about to become free. The shortest case is a worker that has just
 * completed a call and has not polled yet (microseconds); this test widens that window to 20 ms so
 * the rejection is deterministic.
 */
class AdbIoPoolHandoffRaceTest {

    private val pool = newAdbIoExecutor()
    private val park = CountDownLatch(1)

    @AfterEach
    fun tearDown() {
        park.countDown()
        pool.shutdownNow()
    }

    @Test
    fun `a call is not rejected when the last worker frees up moments later`() {
        // ADB_IO_POOL_MAX - 1 workers parked, as on a wedged device.
        val parked = CountDownLatch(ADB_IO_POOL_MAX - 1)
        repeat(ADB_IO_POOL_MAX - 1) {
            pool.execute {
                parked.countDown()
                park.await()
            }
        }
        assertThat(parked.await(10, TimeUnit.SECONDS)).isTrue()

        // The last worker is busy for another 20 ms, then free.
        val lastWorkerStarted = CountDownLatch(1)
        pool.execute {
            lastWorkerStarted.countDown()
            Thread.sleep(20)
        }
        assertThat(lastWorkerStarted.await(10, TimeUnit.SECONDS)).isTrue()

        // Fails with: java.io.IOException: Probe failed: all 16 adb I/O workers are busy
        val result = boundedAdbCall(pool, timeoutMillis = 5_000, operation = "Probe") { 42 }

        assertThat(result).isEqualTo(42)
    }
}
