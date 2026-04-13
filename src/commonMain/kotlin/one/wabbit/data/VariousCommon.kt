package one.wabbit.data

fun String.indent(indent: String): String = split("\n").joinToString("\n") { "$indent$it" }

inline fun <reified V> swap(arr: Array<V>, i: Int, j: Int) {
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

fun <V> swap(list: MutableList<V>, i: Int, j: Int) {
    list[i] = list.set(j, list[i])
}

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

fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}

fun <T> Set<T>.isSubsetOf(that: Set<T>): Boolean = all { it in that }

fun Byte.base16(): String {
    val value = toInt() and 0xFF
    return value.toString(16).padStart(2, '0')
}

fun ByteArray.base16(from: Int = 0, until: Int = size): String {
    val sb = StringBuilder()
    for (i in from until until) {
        sb.append(this[i].base16())
    }
    return sb.toString()
}
