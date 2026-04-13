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

    private val tableRef: AtomicReference<Table<K, V>>
    private val activeMutations = AtomicInt(0)
    private val exclusiveMutationInProgress = AtomicInt(0)
    private val sizeRef = AtomicInt(0)

    actual val size: Int
        get() = sizeRef.load()

    init {
        val bucketCount = normalizeBucketCount(initialCapacity)
        tableRef = AtomicReference(createTable(bucketCount))
    }

    actual operator fun get(key: K): V? = findEntry(tableRef.load(), key)?.value

    actual fun put(key: K, value: V): V? {
        var insertedSize = -1
        val previous =
            withMutationPermit { table ->
                val bucket = table.buckets[bucketIndex(table, key)]
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
                            insertedSize = sizeRef.adjustAndGet(1)
                            return@withMutationPermit null
                        }
                        return@withMutationPermit existing.value
                    }
                }
                error("unreachable")
            }
        if (insertedSize >= 0) {
            resizeIfNeeded(insertedSize)
        }
        return previous
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        var insertedSize = -1
        val previous =
            withMutationPermit { table ->
                val bucket = table.buckets[bucketIndex(table, key)]
                while (true) {
                    val snapshot = bucket.load()
                    val existing = findEntry(snapshot, key)
                    if (existing != null) {
                        return@withMutationPermit existing.value
                    }
                    val updated = Entry(key, value, snapshot)
                    if (bucket.compareAndSet(snapshot, updated)) {
                        hooks.afterBucketMutationBeforeSizeChange?.invoke()
                        insertedSize = sizeRef.adjustAndGet(1)
                        return@withMutationPermit null
                    }
                }
                error("unreachable")
            }
        if (insertedSize >= 0) {
            resizeIfNeeded(insertedSize)
        }
        return previous
    }

    actual fun remove(key: K): V? {
        return withMutationPermit { table ->
            val bucket = table.buckets[bucketIndex(table, key)]
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
        return withMutationPermit { table ->
            val bucket = table.buckets[bucketIndex(table, key)]
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

    actual fun containsKey(key: K): Boolean = findEntry(tableRef.load(), key) != null

    actual fun clear() {
        beginExclusiveMutation()
        try {
            val currentTable = tableRef.load()
            tableRef.store(createTable(currentTable.bucketCount))
            hooks.beforeClearResetsSize?.invoke()
            sizeRef.store(0)
        } finally {
            exclusiveMutationInProgress.store(0)
        }
    }

    actual fun size(): Int = size

    actual fun entriesSnapshot(): List<Pair<K, V>> {
        val table = tableRef.load()
        val result = ArrayList<Pair<K, V>>(sizeRef.load())
        for (bucket in table.buckets) {
            var entry = bucket.load()
            while (entry != null) {
                result += entry.key to entry.value
                entry = entry.next
            }
        }
        return result
    }

    private fun findEntry(table: Table<K, V>, key: K): Entry<K, V>? =
        findEntry(table.buckets[bucketIndex(table, key)].load(), key)

    private fun bucketIndex(table: Table<K, V>, key: K): Int = spreadHash(key.hashCode()) and table.bucketMask

    private inline fun <T> withMutationPermit(block: (Table<K, V>) -> T): T {
        enterMutation()
        try {
            return block(tableRef.load())
        } finally {
            activeMutations.adjust(-1)
        }
    }

    private fun enterMutation() {
        while (true) {
            while (exclusiveMutationInProgress.load() != 0) {
                sched_yield()
            }
            activeMutations.adjust(1)
            if (exclusiveMutationInProgress.load() == 0) {
                return
            }
            activeMutations.adjust(-1)
            sched_yield()
        }
    }

    private fun beginExclusiveMutation() {
        while (!exclusiveMutationInProgress.compareAndSet(0, 1)) {
            sched_yield()
        }
        while (activeMutations.load() != 0) {
            sched_yield()
        }
    }

    private fun resizeIfNeeded(requiredSize: Int) {
        val currentTable = tableRef.load()
        if (requiredSize <= currentTable.resizeThreshold) {
            return
        }

        beginExclusiveMutation()
        try {
            val table = tableRef.load()
            val currentSize = sizeRef.load()
            if (currentSize <= table.resizeThreshold) {
                return
            }
            val newBucketCount = expandedBucketCount(table.bucketCount, currentSize)
            if (newBucketCount == table.bucketCount) {
                return
            }
            val resizedTable = createTable(newBucketCount)
            for (bucket in table.buckets) {
                var entry = bucket.load()
                while (entry != null) {
                    val bucketIndex = bucketIndex(resizedTable, entry.key)
                    val resizedBucket = resizedTable.buckets[bucketIndex]
                    resizedBucket.store(Entry(entry.key, entry.value, resizedBucket.load()))
                    entry = entry.next
                }
            }
            tableRef.store(resizedTable)
            hooks.afterResizePublishesTable?.invoke(table.bucketCount, resizedTable.bucketCount)
        } finally {
            exclusiveMutationInProgress.store(0)
        }
    }

    private fun createTable(bucketCount: Int): Table<K, V> =
        Table(
            bucketCount = bucketCount,
            bucketMask = bucketCount - 1,
            resizeThreshold = resizeThreshold(bucketCount),
            buckets = Array(bucketCount) { AtomicReference<Entry<K, V>?>(null) },
        )

    private data class Entry<K : Any, V : Any>(
        val key: K,
        val value: V,
        val next: Entry<K, V>?,
    )

    private data class Table<K : Any, V : Any>(
        val bucketCount: Int,
        val bucketMask: Int,
        val resizeThreshold: Int,
        val buckets: Array<AtomicReference<Entry<K, V>?>>,
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

    private fun AtomicInt.adjustAndGet(delta: Int): Int {
        while (true) {
            val current = load()
            val updated = current + delta
            if (compareAndSet(current, updated)) {
                return updated
            }
        }
    }
}

private fun normalizeBucketCount(initialCapacity: Int): Int {
    require(initialCapacity >= 0) { "Initial capacity must be non-negative: $initialCapacity" }
    var count = 1
    val target = if (initialCapacity == 0) 16 else initialCapacity
    while (count < target) {
        count = count shl 1
    }
    return maxOf(count, 16)
}

private fun resizeThreshold(bucketCount: Int): Int = maxOf(1, bucketCount - (bucketCount ushr 2))

private fun expandedBucketCount(currentBucketCount: Int, size: Int): Int {
    var nextBucketCount = currentBucketCount
    while (size > resizeThreshold(nextBucketCount)) {
        nextBucketCount = nextBucketCount shl 1
    }
    return nextBucketCount
}

private fun spreadHash(hash: Int): Int {
    val mixed = hash xor (hash ushr 16)
    return mixed and Int.MAX_VALUE
}

internal class ConcurrentHashMapNativeHooks(
    val beforeClearResetsSize: (() -> Unit)? = null,
    val afterBucketMutationBeforeSizeChange: (() -> Unit)? = null,
    val afterResizePublishesTable: ((oldBucketCount: Int, newBucketCount: Int) -> Unit)? = null,
)
