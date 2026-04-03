package one.wabbit.data

internal inline fun binarySearchIndex(
    fromIndex: Int,
    toIndex: Int,
    compareAt: (Int) -> Int,
): Int {
    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
        val mid = (low + high).ushr(1)
        val cmp = compareAt(mid)
        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid
        }
    }

    return -(low + 1)
}
