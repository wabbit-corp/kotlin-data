@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package one.wabbit.data

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference

actual class ConcurrentHashMap<K : Any, V : Any> actual constructor(initialCapacity: Int) {
    private val bucketMask: Int
    private val buckets: Array<AtomicReference<Entry<K, V>?>>
    private val sizeRef = AtomicInt(0)

    init {
        val bucketCount = normalizeBucketCount(initialCapacity)
        bucketMask = bucketCount - 1
        buckets = Array(bucketCount) { AtomicReference<Entry<K, V>?>(null) }
    }

    actual operator fun get(key: K): V? = findEntry(key)?.value

    actual fun put(key: K, value: V): V? {
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
                if (existing == null) {
                    incrementSize()
                    return null
                }
                return existing.value
            }
        }
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        val bucket = buckets[bucketIndex(key)]
        while (true) {
            val snapshot = bucket.load()
            val existing = findEntry(snapshot, key)
            if (existing != null) {
                return existing.value
            }
            val updated = Entry(key, value, snapshot)
            if (bucket.compareAndSet(snapshot, updated)) {
                incrementSize()
                return null
            }
        }
    }

    actual fun remove(key: K): V? {
        val bucket = buckets[bucketIndex(key)]
        while (true) {
            val snapshot = bucket.load()
            val existing = findEntry(snapshot, key) ?: return null
            val updated = removeEntry(snapshot, key)
            if (bucket.compareAndSet(snapshot, updated)) {
                decrementSize()
                return existing.value
            }
        }
    }

    actual fun remove(key: K, value: V): Boolean {
        val bucket = buckets[bucketIndex(key)]
        while (true) {
            val snapshot = bucket.load()
            val existing = findEntry(snapshot, key) ?: return false
            if (existing.value != value) {
                return false
            }
            val updated = removeEntry(snapshot, key)
            if (bucket.compareAndSet(snapshot, updated)) {
                decrementSize()
                return true
            }
        }
    }

    actual fun containsKey(key: K): Boolean = findEntry(key) != null

    actual fun clear() {
        for (bucket in buckets) {
            bucket.store(null)
        }
        sizeRef.store(0)
    }

    actual fun size(): Int = sizeRef.load()

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

    private fun incrementSize() {
        while (true) {
            val current = sizeRef.load()
            if (sizeRef.compareAndSet(current, current + 1)) {
                return
            }
        }
    }

    private fun decrementSize() {
        while (true) {
            val current = sizeRef.load()
            if (sizeRef.compareAndSet(current, current - 1)) {
                return
            }
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

    private fun replaceEntry(head: Entry<K, V>?, key: K, value: V): Entry<K, V>? =
        when {
            head == null -> null
            head.key == key -> Entry(key, value, head.next)
            else -> Entry(head.key, head.value, replaceEntry(head.next, key, value))
        }

    private fun removeEntry(head: Entry<K, V>?, key: K): Entry<K, V>? =
        when {
            head == null -> null
            head.key == key -> head.next
            else -> Entry(head.key, head.value, removeEntry(head.next, key))
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
