# Troubleshooting

## Negative Indices Behave Differently By Method

Primitive buffers support negative indices for documented element access and updates. Immutable
collections such as `Arr` do not support negative indexing. Use method KDoc for exact bounds and
clamping behavior.

## Binary Search Returns A Negative Number

`binarySearch` methods follow the standard insertion-point convention. A negative result means the
value was not found; convert it with `-(index + 1)` to get the insertion point.

Buffer binary search requires the current contents to already be sorted in ascending order. Call
`sort()` or use `sorted()` first when that precondition is not guaranteed.

## Mutating A Buffer Affects Later Reads

Primitive buffers and deques are mutable. Use `copy()`, `toList()`, or primitive-array conversion
when a stable snapshot is required.

## Persistent Updates Do Not Mutate The Original

Persistent structures such as `Arr`, `ArrMap`, `BankersQueue`, and `LeftistHeap` return updated
values. Keep the returned value:

```kotlin
val updated = original.put("key", "value")
```

## Serialization Requires A Format Dependency

The library exposes serializers for supported structures, but applications still need a
serialization format such as `kotlinx-serialization-json`.
