package one.wabbit.data;

class ArrayUtil {
    public static <A> A[] genericArrayOf(A a) {
        return (A[]) new Object[] { a };
    }

    public static <A> A[] emptyGenericArray() {
        return (A[]) new Object[0];
    }
}
