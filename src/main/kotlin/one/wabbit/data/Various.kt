package one.wabbit.data

import java.util.*

fun String.indent(indent: String): String {
    return split("\n").joinToString("\n") { "$indent$it" }
}

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

inline fun <reified V> swap(arr: Array<V>, i: Int, j: Int) {
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

fun <V> swap(list: MutableList<V>, i: Int, j: Int) {
    list[i] = list.set(j, list[i])
}

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

fun <T : Any> mutableWeakSetOf(): MutableSet<T> =
    Collections.newSetFromMap(WeakHashMap<T, Boolean>())

fun <T : Any> mutableWeakSetOf(vararg elements: T): MutableSet<T> =
    elements.toCollection(Collections.newSetFromMap(WeakHashMap(elements.size)))

fun <K : Any, V : Any> mutableWeakHashMapOf(): WeakHashMap<K, V> =
    WeakHashMap<K, V>()

fun <K : Any, V : Any> mutableWeakHashMapOf(vararg elements: Pair<K, V>): WeakHashMap<K, V> {
    val result = WeakHashMap<K, V>(elements.size)
    for ((k, v) in elements) {
        result[k] = v
    }
    return result
}

operator fun <E : Enum<E>> EnumSet<E>.plus(that: EnumSet<E>): EnumSet<E> {
    val set = EnumSet.copyOf(this)
    set.addAll(that)
    return set
}

fun <V> closure(list: List<V>, f: (V) -> List<V>): List<V> {
    val s = HashSet<V>(list)
    val q = java.util.ArrayDeque(s)

    while (q.isNotEmpty()) {
        val m = q.remove()
        f(m).filterTo(q) { it !in s }
        s.add(m)
    }

    return s.toList()
}

fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun <T> Set<T>.isSubsetOf(t: Set<T>): Boolean = this.all { it in t }

fun Double.toStringWithDigits(digits: Int): String {
    assert(digits >= 0)
    return "%.${digits}f".format(this)
}



fun Byte.base16(): String {
    val value = toInt()
    return if (value < 16) {
        "0" + value.toString(16)
    } else {
        value.toString(16)
    }
}
fun ByteArray.base16(from: Int = 0, until: Int = this.size): String {
    val sb = StringBuilder()
    for (i in from until until) {
        sb.append(this[i].base16())
    }
    return sb.toString()
}

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
