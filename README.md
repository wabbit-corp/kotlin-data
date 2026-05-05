# kotlin-data

`kotlin-data` is a Kotlin Multiplatform collection and data-structure library for compact immutable
collections, mutable primitive buffers, persistent queues and heaps, and small functional result
types.

It is intended for projects that need predictable data-structure behavior without pulling in a large
runtime. The library includes immutable value-oriented containers such as `Arr`, `ArrMap`, `Chain`,
`Chunk`, `ConsList`, `LazyList`, and `BankersQueue`; mutable primitive buffers and deques; and
functional types such as `Option`, `Either`, and `Validated`.

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-data:3.0.0")
}
```

## Quick Start

```kotlin
import one.wabbit.data.Arr
import one.wabbit.data.Option
import one.wabbit.data.Validated
import one.wabbit.data.arrOf

val values = arrOf(1, 2, 3)
val doubled: Arr<Int> = values.map { it * 2 }

val maybeFirst: Option<Int> =
    Option.of(values.firstOrNull())

val checked: Validated<String, Int> =
    Validated.succeed(doubled.last())

check(doubled.toList() == listOf(2, 4, 6))
check(maybeFirst.orNull() == 1)
check(checked is Validated.Success)
```

## What Is Included

- `Arr` and `ArrMap`: compact immutable array-backed sequence and small-map types.
- `Chunk`, `Chain`, `ConsList`, and `LazyList`: collection building blocks for persistent and lazy workflows.
- `BankersQueue` and `LeftistHeap`: persistent queue and priority-queue structures.
- `BooleanBuffer`, `IntBuffer`, `DoubleBuffer`, and other primitive buffers: mutable contiguous buffers without boxing.
- `BooleanDeque`, `IntDeque`, `DoubleDeque`, and other primitive deques: mutable ring-buffer deques for both-end operations.
- `Option`, `Either`, and `Validated`: small functional result/value types.
- JVM/Android helpers for weak sets/maps, UUID byte conversion, enum-set copying, and deterministic shuffling.

## Buffer Example

Primitive buffers are mutable and own their internal arrays:

```kotlin
import one.wabbit.data.IntBuffer

val buffer = IntBuffer.of(1, 2, 3)
buffer.add(4)
val removed = buffer.removeAt(0)

check(removed == 1)
check(buffer.toList() == listOf(2, 3, 4))
```

Buffers support negative indices for documented element access and update operations. Range-style
operations clamp like Python slices where documented.

## Persistent Queue Example

```kotlin
import one.wabbit.data.BankersQueue

val queue = BankersQueue.empty<String>()
    .enqueue("first")
    .enqueue("second")

val next = queue.dequeueOrNull()

check(next?.first == "first")
check(next?.second?.peekOrNull() == "second")
```

## Status

This library is stable enough for internal production use, but the public API is still broad and
evolving. Expect documentation and compatibility notes to be maintained as part of publication
prep; avoid depending on undocumented implementation details or internal storage classes.

## Documentation

- [User guide](docs/user-guide.md)
- [API reference notes](docs/api-reference.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Development](docs/development.md)

Generated API docs can be built locally with Dokka. See [API reference notes](docs/api-reference.md)
for the command.

## Release Notes

- [CHANGELOG.md](CHANGELOG.md)

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, contact Wabbit Consulting Corporation at `wabbit@wabbit.one`.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.
