# API Reference

Generated Dokka API docs can be built with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

## Collections

- `Arr<T>`: immutable array-backed sequence with copied inputs and O(1) indexed reads.
- `ArrMap<K, V>`: compact immutable map optimized for small key sets.
- `Chunk<T>`: immutable chunked sequence with slicing, mapping, filtering, and concatenation helpers.
- `Chain<T>`: lazy concatenation tree for efficient append/prepend before materialization.
- `ConsList<T>`: persistent singly linked list.
- `LazyList<T>`: lazily evaluated linked sequence.

## Mutable Primitive Storage

- `*Buffer`: mutable contiguous primitive buffers with explicit capacity management and primitive-array conversions.
- `*Deque`: mutable ring-buffer deques with push/pop operations at both ends.
- Primitive deques expose both `size` and `length`; `length` is a compatibility alias for `size`.
- Buffer and deque element operations throw `IndexOutOfBoundsException` for invalid indices unless the method name ends in `OrNull`.
- Comparable buffers provide `sort`, `sorted`, `binarySearch`, `min`, and `max`; binary search requires already sorted contents.

## Persistent Structures

- `BankersQueue<T>`: persistent FIFO queue backed by lazy front rotation. Use `peek`/`peekOrNull`
  to inspect the next value, and `dequeue`, `dequeueOrNull`, or `uncons` to consume the front value
  with the resulting queue.
- `LeftistHeap<T>`: persistent mergeable min-heap.

## Functional Types

- `Option<T>`: `Some(value)` or `None`, with nullable conversion helpers.
- `Either<L, R>`: `Left(value)` or `Right(value)`, with `map`, `flatMap`, and side-specific transforms.
- `Validated<E, A>`: failure or success with accumulated issues.

## Platform Helpers

JVM/Android source sets add weak collection constructors, deterministic shuffling using
`SplittableRandom`, UUID byte conversion, and enum-set copying helpers.
