# Android: devtools adb I/O pool rejects calls as "all 16 adb I/O workers are busy" while a worker is free (flaky `AdbSocketTimeoutTest`)

## Summary

`newAdbIoExecutor()` in `maestro-client/src/main/java/maestro/android/AdbSocketFactory.kt` (added in #3419)
can reject a call with `IOException: <operation> failed: all 16 adb I/O workers are busy` even though a
worker is free or about to be. This makes the saturation tests in `AdbSocketTimeoutTest` flaky on CI, and
in production it hits exactly the situation the pool exists for: a device that has wedged most of the
workers.

Affected: `v2.11.0` and `main` at `436309bdbd0aca29450a3f0c7fcf8bbe9c3ab027` (the file is unchanged since #3419).

## Root cause

The pool is

```kotlin
ThreadPoolExecutor(0, ADB_IO_POOL_MAX /* 16 */, 60L, TimeUnit.SECONDS, SynchronousQueue())
```

with the default `AbortPolicy`. With a `SynchronousQueue`, `execute()` succeeds only if
(a) a worker is blocked in `poll()` at that exact instant, or (b) the pool is below its cap and can start
a new thread. Otherwise it is rejected.

A worker that has just finished a task still counts towards the cap until it gets back to `poll()`. So
once the pool has 16 threads, a call that arrives while the only non-parked worker is between two tasks
(or a few milliseconds from finishing its current one) is rejected, although that worker would have taken
it almost immediately.

Two consequences:

1. **Production.** When a webview devtools socket wedges, workers park on it (that is what the cap is for).
   With 15 parked workers, every call runs on the 16th one, and calls that land while it is
   finishing the previous one fail with "all 16 adb I/O workers are busy". The client then gives up on a
   socket that would have worked. The same applies to `closeOffThread`: the close falls back to the
   capped throwaway threads, or is dropped, while a worker is about to be free.
2. **Tests.** `saturate()` in `AdbSocketTimeoutTest` interleaves a `connect` and a `read` per socket. If a
   `read` lands in the window above, it is rejected, and `runCatching { input.read() }` swallows the
   error. The pool then never reaches 16 parked workers, and the test fails with
   `the first pool never saturated` (or `saturating reads never parked 16 workers` /
   `the pool never saturated` in the other tests that call `saturate()`). We hit it on GitHub Actions
   (`ubuntu-latest`) in `a saturated pool does not starve bounded calls on a separate pool`, and locally
   once in `close fallback threads are bounded so a wedged transport cannot spawn them without limit`.

## How to reproduce

### Deterministic unit test

Add the test below as `maestro-client/src/test/java/maestro/android/AdbIoPoolHandoffRaceTest.kt` and run

```bash
./gradlew :maestro-client:test --tests "maestro.android.AdbIoPoolHandoffRaceTest"
```

On `main` it fails every time with

```
java.io.IOException: Probe failed: all 16 adb I/O workers are busy
    at maestro.android.AdbSocketFactoryKt.submitAdbCall(AdbSocketFactory.kt)
    at maestro.android.AdbSocketFactoryKt.boundedAdbCall(AdbSocketFactory.kt)
```

(test code: see `AdbIoPoolHandoffRaceTest.kt` next to this file, also pasted below)

### The flaky test, made reproducible

Mark `a saturated pool does not starve bounded calls on a separate pool` with `@RepeatedTest(300)` and
run it while the CPU is busy (e.g. 8 × `sh -c 'while :; do :; done' &` on a 4-core Linux machine, JDK 17).
In our run **18 of 300** repetitions failed with `the first pool never saturated`. Instrumenting the
swallowed reads in `saturate()` showed a rejected read ("adb I/O workers are busy") in every failing
repetition.

## Suggested fix

Give the pool a rejection handler that waits briefly for a worker to take the call. Only a pool whose
workers all stay busy past that grace period counts as saturated; it still fails fast with the same
message, and the 16-thread cap and "never queue" behaviour stay as they are:

```kotlin
internal const val ADB_IO_HANDOFF_GRACE_MILLIS = 100L

internal fun newAdbIoExecutor(): ExecutorService = ThreadPoolExecutor(
    0,
    ADB_IO_POOL_MAX,
    60L,
    TimeUnit.SECONDS,
    SynchronousQueue(),
    { runnable -> Thread(runnable, "AdbSocketIo-${adbIoThreadCount.incrementAndGet()}").apply { isDaemon = true } },
    { runnable, pool ->
        val handedOff = try {
            !pool.isShutdown && pool.queue.offer(runnable, ADB_IO_HANDOFF_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!handedOff) throw RejectedExecutionException("All $ADB_IO_POOL_MAX adb I/O workers are busy")
    },
)
```

With this change, the unit test above passes. Under the same CPU load, 300 repetitions each of the
three existing saturation tests (`a saturated pool does not starve …`, `bounded calls fail fast …`,
`close fallback threads are bounded …`) plus a back-to-back variant passed, 1,200 repetitions in total
with no failures. The rest of `maestro-client`'s tests pass too.

Trade-off: when the pool really is saturated, a rejected call (or `closeOffThread`) now returns after
up to 100 ms instead of immediately.

## Test code

```kotlin
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
```
