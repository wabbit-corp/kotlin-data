// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

/**
 * Multiplatform concurrent mutable hash map abstraction.
 *
 * JVM and Android use the platform concurrent map. Native uses a lock-coordinated implementation
 * that provides snapshot-style entry enumeration.
 */
expect class ConcurrentHashMap<K : Any, V : Any>(initialCapacity: Int = 16) {
    /** Current number of entries. */
    val size: Int

    /** Return the value for [key], or null when no entry exists. */
    operator fun get(key: K): V?

    /** Store [value] for [key] and return the previous value, if any. */
    fun put(key: K, value: V): V?

    /** Store [value] only when [key] is absent and return the previous value, if any. */
    fun putIfAbsent(key: K, value: V): V?

    /** Remove [key] and return the previous value, if any. */
    fun remove(key: K): V?

    /** Remove [key] only when its current value equals [value]. */
    fun remove(key: K, value: V): Boolean

    /** Return whether [key] is present. */
    fun containsKey(key: K): Boolean

    /** Remove all entries from this map. */
    fun clear()

    /** Return the current number of entries. */
    fun size(): Int

    /** Return a stable list of entries observed at the time of the call. */
    fun entriesSnapshot(): List<Pair<K, V>>
}
