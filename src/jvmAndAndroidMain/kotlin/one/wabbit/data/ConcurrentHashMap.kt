package one.wabbit.data

import java.util.concurrent.ConcurrentHashMap as JConcurrentHashMap

actual class ConcurrentHashMap<K : Any, V : Any> actual constructor(initialCapacity: Int) {
    private val delegate = JConcurrentHashMap<K, V>(initialCapacity)

    actual val size: Int
        get() = delegate.size

    actual operator fun get(key: K): V? = delegate[key]

    actual fun put(key: K, value: V): V? = delegate.put(key, value)

    actual fun putIfAbsent(key: K, value: V): V? = delegate.putIfAbsent(key, value)

    actual fun remove(key: K): V? = delegate.remove(key)

    actual fun remove(key: K, value: V): Boolean = delegate.remove(key, value)

    actual fun containsKey(key: K): Boolean = delegate.containsKey(key)

    actual fun clear() {
        delegate.clear()
    }

    actual fun size(): Int = size

    actual fun entriesSnapshot(): List<Pair<K, V>> =
        delegate.entries.map { it.key to it.value }
}
