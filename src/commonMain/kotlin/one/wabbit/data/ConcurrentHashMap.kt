package one.wabbit.data

expect class ConcurrentHashMap<K : Any, V : Any>(initialCapacity: Int = 16) {
    operator fun get(key: K): V?

    fun put(key: K, value: V): V?

    fun putIfAbsent(key: K, value: V): V?

    fun remove(key: K): V?

    fun remove(key: K, value: V): Boolean

    fun containsKey(key: K): Boolean

    fun clear()

    fun size(): Int

    fun entriesSnapshot(): List<Pair<K, V>>
}
