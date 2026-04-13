@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package one.wabbit.data

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.posix.__darwin_pthread_tVar
import platform.posix.pthread_create
import platform.posix.pthread_join
import platform.posix.pthread_t
import platform.posix.sched_yield

private fun concurrentHashMapTestThreadEntry(arg: COpaquePointer?): COpaquePointer? {
    val block = arg!!.asStableRef<() -> Unit>()
    try {
        block.get().invoke()
    } finally {
        block.dispose()
    }
    return null
}

private fun startNativeThread(block: () -> Unit): pthread_t =
    memScoped {
        val thread = alloc<__darwin_pthread_tVar>()
        val stableBlock = StableRef.create(block)
        val result =
            pthread_create(
                thread.ptr,
                null,
                staticCFunction(::concurrentHashMapTestThreadEntry),
                stableBlock.asCPointer(),
            )
        check(result == 0) { "pthread_create failed: $result" }
        checkNotNull(thread.value)
    }

private fun joinNativeThread(thread: pthread_t) {
    val result = pthread_join(thread, null)
    check(result == 0) { "pthread_join failed: $result" }
}

private fun waitUntil(timeout: Duration, condition: () -> Boolean): Boolean {
    val start = TimeSource.Monotonic.markNow()
    while (!condition()) {
        if (start.elapsedNow() >= timeout) {
            return false
        }
        sched_yield()
    }
    return true
}

class ConcurrentHashMapNativeSpec {
    @Test
    fun `clear blocks puts until size reset is published`() {
        val clearPaused = AtomicInt(0)
        val allowClearToFinish = AtomicInt(0)
        val putStarted = AtomicInt(0)
        val putFinished = AtomicInt(0)
        val putProgressedDuringClear = AtomicInt(0)

        val map =
            ConcurrentHashMap<String, Int>(
                16,
                ConcurrentHashMapNativeHooks(
                    beforeClearResetsSize = {
                        clearPaused.store(1)
                        while (allowClearToFinish.load() == 0) {
                            sched_yield()
                        }
                    },
                    afterBucketMutationBeforeSizeChange = {
                        if (clearPaused.load() != 0 && allowClearToFinish.load() == 0) {
                            putProgressedDuringClear.store(1)
                        }
                    },
                ),
            )
        map.put("seed", 1)

        var clearThread: pthread_t? = null
        var putThread: pthread_t? = null

        try {
            clearThread = startNativeThread { map.clear() }
            assertTrue(waitUntil(2.seconds) { clearPaused.load() == 1 }, "clear never reached its critical section")

            putThread = startNativeThread {
                putStarted.store(1)
                map.put("late", 2)
                putFinished.store(1)
            }

            assertTrue(waitUntil(2.seconds) { putStarted.load() == 1 }, "put thread never started")
            assertFalse(
                waitUntil(500.milliseconds) { putProgressedDuringClear.load() == 1 },
                "put mutated a bucket while clear was still publishing an empty size",
            )
        } finally {
            allowClearToFinish.store(1)
            clearThread?.let(::joinNativeThread)
            putThread?.let(::joinNativeThread)
        }

        assertEquals(1, putFinished.load())
        assertEquals(1, map.size())
        assertFalse(map.containsKey("seed"))
        assertEquals(2, map["late"])
        val entries: Set<Pair<String, Int>> = map.entriesSnapshot().toSet()
        assertEquals(setOf(Pair("late", 2)), entries)
    }
}
