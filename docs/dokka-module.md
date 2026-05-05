# Module kotlin-data

`kotlin-data` provides Kotlin Multiplatform data structures and utility types for applications that
need compact immutable collections, mutable primitive buffers, persistent queues/heaps, and small
functional value types.

## Installation

```kotlin
dependencies {
    implementation("one.wabbit:kotlin-data:3.0.0")
}
```

## Main API Groups

- Immutable collections: `Arr`, `ArrMap`, `Chunk`, `Chain`, `ConsList`, and `LazyList`.
- Persistent structures: `BankersQueue` and `LeftistHeap`.
- Mutable primitive storage: `BooleanBuffer`, `ByteBuffer`, `IntBuffer`, and related buffer types.
- Mutable primitive deques: `BooleanDeque`, `ByteDeque`, `IntDeque`, and related deque types.
- Functional values: `Option`, `Either`, and `Validated`.
- Platform helpers: weak collections, UUID conversion, deterministic shuffling, and small text utilities.

## Quick Start

```kotlin
import one.wabbit.data.IntBuffer
import one.wabbit.data.arrOf

val immutable = arrOf("a", "b", "c")
val upper = immutable.map(String::uppercase)

val mutable = IntBuffer.of(1, 2, 3)
mutable.add(4)

check(upper.toList() == listOf("A", "B", "C"))
check(mutable.toList() == listOf(1, 2, 3, 4))
```

## API Notes

Most immutable structures copy caller-provided arrays or collections before storing them. Mutable
buffers and deques update receivers in place and expose explicit copy/conversion functions when a
snapshot is needed.
