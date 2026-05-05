// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

/** Prefix every line in this string with [indent]. */
fun String.indent(indent: String): String = split("\n").joinToString("\n") { "$indent$it" }

/** Swap elements [i] and [j] in [arr]. */
inline fun <reified V> swap(arr: Array<V>, i: Int, j: Int) {
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

/** Swap elements [i] and [j] in [list]. */
fun <V> swap(list: MutableList<V>, i: Int, j: Int) {
    list[i] = list.set(j, list[i])
}

/**
 * Compute the transitive closure starting from [list] by repeatedly expanding each value with [f].
 */
fun <V> closure(list: List<V>, f: (V) -> List<V>): List<V> {
    val seen = HashSet<V>(list)
    val queue = ArrayDeque(seen)

    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        for (next in f(item)) {
            if (seen.add(next)) {
                queue.addLast(next)
            }
        }
    }

    return seen.toList()
}

/** Capitalize the first character using titlecase semantics. */
fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}

/** Return whether this set is a subset of [that]. */
fun <T> Set<T>.isSubsetOf(that: Set<T>): Boolean = all { it in that }

/** Render this byte as a two-character lowercase hexadecimal string. */
fun Byte.base16(): String {
    val value = toInt() and 0xFF
    return value.toString(16).padStart(2, '0')
}

/** Render bytes in the half-open range `[from, until)` as lowercase hexadecimal. */
fun ByteArray.base16(from: Int = 0, until: Int = size): String {
    val sb = StringBuilder()
    for (i in from until until) {
        sb.append(this[i].base16())
    }
    return sb.toString()
}
