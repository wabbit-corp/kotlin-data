@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package one.wabbit.data

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import platform.posix.sched_yield

actual class ConcurrentHashMap<K : Any, V : Any> internal constructor(
    initialCapacity: Int,
    private val hooks: ConcurrentHashMapNativeHooks = ConcurrentHashMapNativeHooks(),
) {
    actual constructor(initialCapacity: Int) : this(initialCapacity, ConcurrentHashMapNativeHooks())

    private val bucketMask: Int
    private val buckets: Array<AtomicReference<Entry<K, V>?>>
    private val activeMutations = AtomicInt(0)
    private val clearInProgress = AtomicInt(0)
    private val sizeRef = AtomicInt(0)

    actual val size: Int
        get() = sizeRef.load()

    init {
        val bucketCount = normalizeBucketCount(initialCapacity)
        bucketMask = bucketCount - 1
        buckets = Array(bucketCount) { AtomicReference<Entry<K, V>?>(null) }
    }

    actual operator fun get(key: K): V? = findEntry(key)?.value

    actual fun put(key: K, value: V): V? {
        return withMutationPermit {
            val bucket = buckets[bucketIndex(key)]
            while (true) {
                val snapshot = bucket.load()
                val existing = findEntry(snapshot, key)
                val updated = if (existing == null) {
                    Entry(key, value, snapshot)
                } else {
                    replaceEntry(snapshot, key, value)
                }
                if (bucket.compareAndSet(snapshot, updated)) {
                    hooks.afterBucketMutationBeforeSizeChange?.invoke()
                    if (existing == null) {
                        sizeRef.adjust(1)
                        return@withMutationPermit null
                    }
                    return@withMutationPermit existing.value
                }
            }
            error("unreachable")
        }
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        return withMutationPermit {
            val bucket = buckets[bucketIndex(key)]
            while (true) {
                val snapshot = bucket.load()
                val existing = findEntry(snapshot, key)
                if (existing != null) {
                    return@withMutationPermit existing.value
                }
                val updated = Entry(key, value, snapshot)
                if (bucket.compareAndSet(snapshot, updated)) {
                    hooks.afterBucketMutationBeforeSizeChange?.invoke()
                    sizeRef.adjust(1)
                    return@withMutationPermit null
                }
            }
            error("unreachable")
        }
    }

    actual fun remove(key: K): V? {
        return withMutationPermit {
            val bucket = buckets[bucketIndex(key)]
            while (true) {
                val snapshot = bucket.load()
                val existing = findEntry(snapshot, key) ?: return@withMutationPermit null
                val updated = removeEntry(snapshot, key)
                if (bucket.compareAndSet(snapshot, updated)) {
                    hooks.afterBucketMutationBeforeSizeChange?.invoke()
                    sizeRef.adjust(-1)
                    return@withMutationPermit existing.value
                }
            }
            error("unreachable")
        }
    }

    actual fun remove(key: K, value: V): Boolean {
        return withMutationPermit {
            val bucket = buckets[bucketIndex(key)]
            while (true) {
                val snapshot = bucket.load()
                val existing = findEntry(snapshot, key) ?: return@withMutationPermit false
                if (existing.value != value) {
                    return@withMutationPermit false
                }
                val updated = removeEntry(snapshot, key)
                if (bucket.compareAndSet(snapshot, updated)) {
                    hooks.afterBucketMutationBeforeSizeChange?.invoke()
                    sizeRef.adjust(-1)
                    return@withMutationPermit true
                }
            }
            error("unreachable")
        }
    }

    actual fun containsKey(key: K): Boolean = findEntry(key) != null

    actual fun clear() {
        beginClear()
        try {
            for (bucket in buckets) {
                bucket.store(null)
            }
            hooks.beforeClearResetsSize?.invoke()
            sizeRef.store(0)
        } finally {
            clearInProgress.store(0)
        }
    }

    actual fun size(): Int = size

    actual fun entriesSnapshot(): List<Pair<K, V>> {
        val result = ArrayList<Pair<K, V>>(sizeRef.load())
        for (bucket in buckets) {
            var entry = bucket.load()
            while (entry != null) {
                result += entry.key to entry.value
                entry = entry.next
            }
        }
        return result
    }

    private fun findEntry(key: K): Entry<K, V>? = findEntry(buckets[bucketIndex(key)].load(), key)

    private fun bucketIndex(key: K): Int = spreadHash(key.hashCode()) and bucketMask

    private inline fun <T> withMutationPermit(block: () -> T): T {
        enterMutation()
        try {
            return block()
        } finally {
            activeMutations.adjust(-1)
        }
    }

    private fun enterMutation() {
        while (true) {
            while (clearInProgress.load() != 0) {
                sched_yield()
            }
            activeMutations.adjust(1)
            if (clearInProgress.load() == 0) {
                return
            }
            activeMutations.adjust(-1)
            sched_yield()
        }
    }

    private fun beginClear() {
        while (!clearInProgress.compareAndSet(0, 1)) {
            sched_yield()
        }
        while (activeMutations.load() != 0) {
            sched_yield()
        }
    }

    private data class Entry<K : Any, V : Any>(
        val key: K,
        val value: V,
        val next: Entry<K, V>?,
    )

    private fun findEntry(head: Entry<K, V>?, key: K): Entry<K, V>? {
        var current = head
        while (current != null) {
            if (current.key == key) {
                return current
            }
            current = current.next
        }
        return null
    }

    private fun replaceEntry(head: Entry<K, V>?, key: K, value: V): Entry<K, V>? {
        if (head == null) return null

        val prefix = ArrayList<Entry<K, V>>()
        var current = head
        while (current != null && current.key != key) {
            prefix += current
            current = current.next
        }
        if (current == null) return head

        var rebuilt: Entry<K, V>? = Entry(key, value, current.next)
        for (index in prefix.lastIndex downTo 0) {
            val entry = prefix[index]
            rebuilt = Entry(entry.key, entry.value, rebuilt)
        }
        return rebuilt
    }

    private fun removeEntry(head: Entry<K, V>?, key: K): Entry<K, V>? {
        if (head == null) return null

        val prefix = ArrayList<Entry<K, V>>()
        var current = head
        while (current != null && current.key != key) {
            prefix += current
            current = current.next
        }
        if (current == null) return head

        var rebuilt = current.next
        for (index in prefix.lastIndex downTo 0) {
            val entry = prefix[index]
            rebuilt = Entry(entry.key, entry.value, rebuilt)
        }
        return rebuilt
    }

    private fun AtomicInt.adjust(delta: Int) {
        while (true) {
            val current = load()
            if (compareAndSet(current, current + delta)) {
                return
            }
        }
    }
}

private fun normalizeBucketCount(initialCapacity: Int): Int {
    var count = 1
    val target = if (initialCapacity <= 0) 16 else initialCapacity
    while (count < target) {
        count = count shl 1
    }
    return maxOf(count, 16)
}

private fun spreadHash(hash: Int): Int {
    val mixed = hash xor (hash ushr 16)
    return mixed and Int.MAX_VALUE
}

internal class ConcurrentHashMapNativeHooks(
    val beforeClearResetsSize: (() -> Unit)? = null,
    val afterBucketMutationBeforeSizeChange: (() -> Unit)? = null,
)
