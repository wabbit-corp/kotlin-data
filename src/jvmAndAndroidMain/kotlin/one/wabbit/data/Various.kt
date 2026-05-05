// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

import java.util.Collections
import java.util.EnumSet
import java.util.SplittableRandom
import java.util.UUID
import java.util.WeakHashMap

/** Return a shuffled copy of this iterable using [random]. */
fun <T> Iterable<T>.shuffled(random: SplittableRandom): List<T> {
    val list = toMutableList()

    for (i in list.indices) {
        val j = random.nextInt(i, list.size)
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }

    return list
}

/** Shuffle [list] in place using [rnd]. */
inline fun <reified V> shuffle(list: MutableList<V>, rnd: SplittableRandom) {
    val SHUFFLE_THRESHOLD = 5
    val size = list.size
    if (size < SHUFFLE_THRESHOLD || list is RandomAccess) {
        for (i in size downTo 2) swap(list, i - 1, rnd.nextInt(i))
    } else {
        val arr: Array<V> = list.toTypedArray()

        // Shuffle array
        for (i in size downTo 2) swap(arr, i - 1, rnd.nextInt(i))

        // Dump array back into list
        val it = list.listIterator()
        for (e in arr) {
            it.next()
            it.set(e)
        }
    }
}

/** Create an empty mutable set whose entries are held weakly. */
fun <T : Any> mutableWeakSetOf(): MutableSet<T> =
    Collections.newSetFromMap(WeakHashMap<T, Boolean>())

/** Create a mutable weak set containing [elements]. */
fun <T : Any> mutableWeakSetOf(vararg elements: T): MutableSet<T> =
    elements.toCollection(Collections.newSetFromMap(WeakHashMap(elements.size)))

/** Create an empty mutable weak hash map. */
fun <K : Any, V : Any> mutableWeakHashMapOf(): WeakHashMap<K, V> = WeakHashMap<K, V>()

/** Create a mutable weak hash map containing [elements]. */
fun <K : Any, V : Any> mutableWeakHashMapOf(vararg elements: Pair<K, V>): WeakHashMap<K, V> {
    val result = WeakHashMap<K, V>(elements.size)
    for ((k, v) in elements) {
        result[k] = v
    }
    return result
}

/** Return a copy of this enum set with all values from [that] added. */
operator fun <E : Enum<E>> EnumSet<E>.plus(that: EnumSet<E>): EnumSet<E> {
    val set = EnumSet.copyOf(this)
    set.addAll(that)
    return set
}

/** Format this double with exactly [digits] digits after the decimal point. */
fun Double.toStringWithDigits(digits: Int): String {
    assert(digits >= 0)
    return "%.${digits}f".format(this)
}

/** Return this UUID as a 16-byte big-endian byte array. */
fun UUID.toByteArray(): ByteArray {
    val bytes = ByteArray(16)
    val msb = mostSignificantBits
    val lsb = leastSignificantBits
    for (i in 0..7) {
        bytes[i] = (msb shr (7 - i) * 8).toByte()
        bytes[i + 8] = (lsb shr (7 - i) * 8).toByte()
    }
    return bytes
}
